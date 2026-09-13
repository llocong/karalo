// Adapted from TeamNewPipe/NewPipe (GPL-3.0), app/src/main/java/org/schabi/newpipe/util/potoken/PoTokenProviderImpl.kt
//
// Deviates from upstream by using Coroutines (a Mutex + suspend functions, bridged to
// PoTokenProvider's synchronous interface with runBlocking — safe here since NewPipeExtractor
// always calls this from a background dispatcher, never the main thread) instead of RxJava3.
package com.karalo.youtubeclient.internal.potoken

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mints YouTube "proof of origin" tokens for the WEB InnerTube client by running Google's BotGuard
 * JS challenge in a hidden [android.webkit.WebView] (see [PoTokenWebView]).
 *
 * Only [getWebClientPoToken] is implemented, matching upstream TeamNewPipe/NewPipe's own scope:
 * Android/iOS client poTokens need Google's undocumented DroidGuard/iosGuard mechanisms, not
 * BotGuard, and nobody has reverse-engineered those.
 *
 * Public (not internal): YouTubeClientModule.provideDownloader's parameter type must be at least
 * as visible as that function itself (same reasoning as NewPipeYouTubeClient).
 */
@Singleton
class WebViewPoTokenProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
    ) : PoTokenProvider {
        private val webViewSupported by lazy { supportsWebView() }
        private var webViewBadImpl = false

        private val mutex = Mutex()
        private var visitorData: String? = null
        private var streamingPoToken: String? = null
        private var generator: PoTokenGenerator? = null

        override fun getWebClientPoToken(videoId: String): PoTokenResult? {
            if (!webViewSupported || webViewBadImpl) return null

            return try {
                runBlocking { getWebClientPoTokenSuspend(videoId, forceRecreate = false) }
            } catch (e: BadWebViewException) {
                Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                webViewBadImpl = true
                null
            }
        }

        override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null

        override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null

        override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

        /**
         * @param forceRecreate whether to force the recreation of [generator], to be used in case
         * the current [generator] threw an error last time a poToken was generated
         */
        private suspend fun getWebClientPoTokenSuspend(
            videoId: String,
            forceRecreate: Boolean,
        ): PoTokenResult {
            val init =
                mutex.withLock {
                    val shouldRecreate = generator == null || forceRecreate || generator!!.isExpired()

                    if (shouldRecreate) {
                        val requestInfo = InnertubeClientRequestInfo.ofWebClient()
                        requestInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()

                        visitorData =
                            YoutubeParsingHelper.getVisitorDataFromInnertube(
                                requestInfo,
                                NewPipe.getPreferredLocalization(),
                                NewPipe.getPreferredContentCountry(),
                                YoutubeParsingHelper.getYouTubeHeaders(),
                                YoutubeParsingHelper.YOUTUBEI_V1_URL,
                                null,
                                false,
                            )

                        generator?.close()
                        generator = PoTokenWebView.newPoTokenGenerator(context, okHttpClient)

                        // The streaming poToken needs to be generated exactly once before
                        // generating any other (player) tokens.
                        streamingPoToken = generator!!.generatePoToken(visitorData!!)
                    }

                    Init(generator!!, visitorData!!, streamingPoToken!!, shouldRecreate)
                }

            val playerPoToken =
                try {
                    // Not holding the mutex here: the generator can mint multiple poTokens in
                    // parallel; the only thing that must happen exactly once is the streaming
                    // poToken generation above.
                    init.generator.generatePoToken(videoId)
                } catch (
                    @Suppress("TooGenericExceptionCaught") t: Throwable,
                ) {
                    // Deliberately broad: any failure minting this poToken should either retry
                    // with a fresh generator or propagate, not crash the caller.
                    if (init.hasBeenRecreated) {
                        // the generator has just been (re)created, so there is likely nothing
                        // more we can do
                        throw t
                    } else {
                        // retry, this time recreating the generator from scratch — this can
                        // happen e.g. if the app went to the background and the WebView's
                        // content was lost
                        Log.e(TAG, "Failed to obtain poToken, retrying", t)
                        return getWebClientPoTokenSuspend(videoId, forceRecreate = true)
                    }
                }

            return PoTokenResult(init.visitorData, playerPoToken, init.streamingPoToken)
        }

        private data class Init(
            val generator: PoTokenGenerator,
            val visitorData: String,
            val streamingPoToken: String,
            val hasBeenRecreated: Boolean,
        )

        private companion object {
            val TAG = WebViewPoTokenProvider::class.simpleName

            fun supportsWebView(): Boolean =
                try {
                    CookieManager.getInstance()
                    true
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") t: Throwable,
                ) {
                    // Deliberately broad and silent: this is how NewPipe's own DeviceUtils checks
                    // for the (expected, not exceptional) absence of a WebView implementation.
                    false
                }
        }
    }
