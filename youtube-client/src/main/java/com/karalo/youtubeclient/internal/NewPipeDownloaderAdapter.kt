package com.karalo.youtubeclient.internal

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Bridges NewPipeExtractor's [Downloader] abstraction onto the app's shared OkHttp client
 * (`:core-network`), following the same pattern the upstream NewPipe Android app uses for its own
 * `DownloaderImpl`.
 *
 * NOTE: verify this against the exact API of the pinned NewPipeExtractor version (see
 * gradle/libs.versions.toml) during the integration spike — [Request]/[Response] constructor
 * signatures have shifted across NewPipeExtractor releases.
 */
internal class NewPipeDownloaderAdapter(
    private val client: OkHttpClient,
) : Downloader() {
    override fun execute(request: Request): Response {
        val dataToSend = request.dataToSend()
        val requestBody = dataToSend?.toRequestBody()

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(request.httpMethod(), requestBody)
                .url(request.url())
                .header("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            requestBuilder.removeHeader(name)
            values.forEach { value -> requestBuilder.addHeader(name, value) }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                throw ReCaptchaException("reCAPTCHA challenge requested", request.url())
            }

            val body = response.body?.string().orEmpty()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                body,
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Karalo) AppleWebKit/537.36"
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
