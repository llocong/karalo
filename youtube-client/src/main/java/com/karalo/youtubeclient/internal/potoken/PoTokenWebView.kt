// Adapted from TeamNewPipe/NewPipe (GPL-3.0), app/src/main/java/org/schabi/newpipe/util/potoken/PoTokenWebView.kt
//
// Deviates from upstream in two ways:
// - Coroutines (suspendCancellableCoroutine + a private CoroutineScope) instead of RxJava3's
//   Single/SingleEmitter/CompositeDisposable — this project has no RxJava dependency anywhere.
// - The shared, injected OkHttpClient (same one NewPipeDownloaderAdapter uses) instead of
//   NewPipe's own DownloaderImpl singleton, for the two BotGuard service requests.
package com.karalo.youtubeclient.internal.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PoTokenWebView private constructor(
    context: Context,
    private val okHttpClient: OkHttpClient,
) : PoTokenGenerator {
    private val webView = WebView(context)

    // Used only while initializing (loading the BotGuard VM and obtaining an integrityToken).
    private var initContinuation: CancellableContinuation<Unit>? = null

    // Used only for network + JS-driving work kicked off from @JavascriptInterface callbacks,
    // which run on a WebView-owned thread, not the main thread and not a coroutine context.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val poTokenContinuations = mutableListOf<Pair<String, CancellableContinuation<String>>>()
    private lateinit var expirationInstant: Instant

    // region Initialization
    init {
        val webViewSettings = webView.settings
        // noinspection SetJavaScriptEnabled we want to use JavaScript!
        webViewSettings.javaScriptEnabled = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webViewSettings, false)
        }
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView does not need internet access

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.message().contains("Uncaught")) {
                        // There should not be any uncaught errors while executing the code,
                        // because everything that can fail is guarded by try-catch. Therefore,
                        // this likely indicates that there was a syntax error in the code, i.e.
                        // the WebView only supports a really old version of JS.
                        val fmt = "\"${m.message()}\", source: ${m.sourceId()} (${m.lineNumber()})"
                        val exception = BadWebViewException(fmt)
                        Log.e(TAG, "This WebView implementation is broken: $fmt")

                        onInitializationErrorCloseAndCancel(exception)
                        popAllPoTokenContinuations().forEach { (_, continuation) ->
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }
                    }
                    return super.onConsoleMessage(m)
                }
            }
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. Suspends until BotGuard has loaded, run, and produced an `integrityToken`
     * (or throws if any step fails).
     */
    private suspend fun loadHtmlAndObtainBotguard(context: Context) {
        val html =
            withContext(Dispatchers.IO) {
                context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
            }
        withContext(Dispatchers.Main) {
            webView.loadDataWithBaseURL(
                "https://www.youtube.com",
                html.replaceFirst(
                    "</script>",
                    // calls downloadAndRunBotguard() when the page has finished loading
                    "\n$JS_INTERFACE.downloadAndRunBotguard()</script>",
                ),
                "text/html",
                "utf-8",
                null,
            )
        }
        suspendCancellableCoroutine { continuation -> initContinuation = continuation }
    }

    /**
     * Called during initialization by the JavaScript snippet appended to the HTML page content in
     * [loadHtmlAndObtainBotguard] after the WebView content has been loaded.
     */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        scope.launch {
            try {
                val responseBody =
                    makeBotguardServiceRequest(
                        "https://www.youtube.com/api/jnn/v1/Create",
                        "[ \"$REQUEST_KEY\" ]",
                    )
                val parsedChallengeData = parseChallengeData(responseBody)
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        """try {
                            data = $parsedChallengeData
                            runBotGuard(data).then(function (result) {
                                this.webPoSignalOutput = result.webPoSignalOutput
                                $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                            }, function (error) {
                                $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                            })
                        } catch (error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                        }""",
                        null,
                    )
                }
            } catch (t: Throwable) {
                onInitializationErrorCloseAndCancel(t)
            }
        }
    }

    /**
     * Called during initialization by the JavaScript snippets from either
     * [downloadAndRunBotguard] or [onRunBotguardResult].
     */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        Log.e(TAG, "Initialization error from JavaScript: $error")
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        scope.launch {
            try {
                val responseBody =
                    makeBotguardServiceRequest(
                        "https://www.youtube.com/api/jnn/v1/GenerateIT",
                        "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
                    )
                val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)
                // leave 10 minutes of margin just to be sure
                expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds - 600)

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                        initContinuation?.let { continuation ->
                            initContinuation = null
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
            } catch (t: Throwable) {
                onInitializationErrorCloseAndCancel(t)
            }
        }
    }
    // endregion

    // region Obtaining poTokens
    override suspend fun generatePoToken(identifier: String): String =
        suspendCancellableCoroutine { continuation ->
            addPoTokenContinuation(identifier, continuation)
            Handler(Looper.getMainLooper()).post {
                val u8Identifier = stringToU8(identifier)
                webView.evaluateJavascript(
                    """try {
                            identifier = "$identifier"
                            u8Identifier = $u8Identifier
                            poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                            poTokenU8String = ""
                            for (i = 0; i < poTokenU8.length; i++) {
                                if (i != 0) poTokenU8String += ","
                                poTokenU8String += poTokenU8[i]
                            }
                            $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                        } catch (error) {
                            $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                        }""",
                ) {}
            }
        }

    /**
     * Called by the JavaScript snippet from [generatePoToken] when an error occurs in calling the
     * JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenError(
        identifier: String,
        error: String,
    ) {
        popPoTokenContinuation(identifier)?.let { continuation ->
            if (continuation.isActive) continuation.resumeWithException(buildExceptionForJsError(error))
        }
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenResult(
        identifier: String,
        poTokenU8: String,
    ) {
        val continuation = popPoTokenContinuation(identifier) ?: return
        val poToken =
            try {
                u8ToBase64(poTokenU8)
            } catch (t: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(t)
                return
            }
        if (continuation.isActive) continuation.resume(poToken)
    }

    override fun isExpired(): Boolean = Instant.now().isAfter(expirationInstant)
    // endregion

    // region Handling multiple continuations

    /**
     * Adds the ([identifier], [continuation]) pair to [poTokenContinuations]. This makes it so
     * that multiple poToken requests can be generated in parallel, and the results will be
     * notified to the right continuation.
     */
    private fun addPoTokenContinuation(
        identifier: String,
        continuation: CancellableContinuation<String>,
    ) {
        synchronized(poTokenContinuations) {
            poTokenContinuations.add(Pair(identifier, continuation))
        }
    }

    /**
     * Extracts and removes from [poTokenContinuations] a continuation based on its [identifier].
     * The continuation is supposed to be used immediately after to either resume with a result or
     * an exception.
     */
    private fun popPoTokenContinuation(identifier: String): CancellableContinuation<String>? =
        synchronized(poTokenContinuations) {
            poTokenContinuations.indexOfFirst { it.first == identifier }.takeIf { it >= 0 }?.let {
                poTokenContinuations.removeAt(it).second
            }
        }

    /**
     * Clears [poTokenContinuations] and returns its previous contents.
     */
    private fun popAllPoTokenContinuations(): List<Pair<String, CancellableContinuation<String>>> =
        synchronized(poTokenContinuations) {
            val result = poTokenContinuations.toList()
            poTokenContinuations.clear()
            result
        }
    // endregion

    // region Utils

    /**
     * Makes a POST request to [url] with the given [data] by setting the correct headers. Only
     * used during initialization — any error here is fatal to initialization and propagates to
     * the caller.
     */
    private suspend fun makeBotguardServiceRequest(
        url: String,
        data: String,
    ): String =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json+protobuf")
                    .header("x-goog-api-key", GOOGLE_API_KEY)
                    .header("x-user-agent", "grpc-web-javascript/0.1")
                    .post(data.toRequestBody())
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PoTokenException("Invalid response code: ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        }

    /**
     * Handles any error happening during initialization, releasing resources and propagating the
     * error to whoever is suspended waiting for initialization (or for a poToken, if
     * initialization had already finished when this instance became unusable).
     */
    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        Handler(Looper.getMainLooper()).post {
            close()
            initContinuation?.let { continuation ->
                initContinuation = null
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            popAllPoTokenContinuations().forEach { (_, continuation) ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    /**
     * Releases all [webView] and [scope] resources.
     */
    @MainThread
    override fun close() {
        scope.cancel()

        webView.clearHistory()
        // clears RAM cache and disk cache (globally for all WebViews)
        webView.clearCache(true)

        // ensures that the WebView isn't doing anything when destroying it
        webView.loadUrl("about:blank")

        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }
    // endregion

    companion object {
        private val TAG = PoTokenWebView::class.simpleName

        // Public API key used by BotGuard, obtained by looking at BotGuard requests (same key
        // TeamNewPipe/NewPipe's own PoTokenWebView uses — this is not a Karalo/YouTube secret).
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw" // NOSONAR
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"
        private const val ASSET_FILE_NAME = "po_token.html"

        /**
         * Initializes a [PoTokenGenerator] by loading the BotGuard VM, running it, and obtaining
         * an `integrityToken`. Can then be used multiple times to generate multiple poTokens with
         * [PoTokenGenerator.generatePoToken].
         */
        suspend fun newPoTokenGenerator(
            context: Context,
            okHttpClient: OkHttpClient,
        ): PoTokenGenerator {
            val potWv =
                withContext(Dispatchers.Main) {
                    PoTokenWebView(context, okHttpClient)
                }
            potWv.loadHtmlAndObtainBotguard(context)
            return potWv
        }
    }
}
