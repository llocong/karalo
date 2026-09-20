package com.karalo.backend.youtube

import com.karalo.backend.domain.ApiException
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicBoolean

// Mirrors the app's `KaraokeQueryFormatter` -- every search is silently prefixed with "karaoke "
// so results skew toward karaoke versions, same as the TV's own search.
private const val KARAOKE_QUERY_PREFIX = "karaoke "

/**
 * Serves the mobile web page's search requests using the SAME underlying approach as the TV's
 * `:youtube-client` (NewPipeExtractor, same pinned fork/tag) — re-hosted here rather than shared
 * as a Gradle source set because `backend/` is a deliberately independent Gradle build (see this
 * feature's implementation plan for why), and because `:youtube-client`'s stream-resolution path
 * needs an Android WebView for its BotGuard/PoToken challenge solver, which this plain-JVM search
 * path deliberately never touches (confirmed: NewPipeYouTubeClient.search()/suggestions() never
 * call into that solver — only streamsFor() does, and streamsFor() stays 100% on the TV,
 * unchanged). Verified, not just asserted: a real search against live YouTube from this exact
 * dependency on a headless JVM succeeded during this feature's implementation (see git history /
 * the karaoke ADR) before any of the rest of this backend was written.
 *
 * Mirrors `NewPipeYouTubeClient.search()`'s exact call shape and `YtMapper`'s exact
 * thumbnail-selection/video-id-extraction logic so results are equivalent, not just similar, to
 * what the TV itself would show for the same query.
 */
class BackendYouTubeSearch(
    okHttpClient: OkHttpClient,
) {
    private val service = ServiceList.YouTube

    init {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(JvmDownloader(okHttpClient))
        }
    }

    fun search(query: String): List<Result> =
        try {
            val trimmed = query.trim()
            val karaokeQuery = if (trimmed.startsWith(KARAOKE_QUERY_PREFIX, ignoreCase = true)) trimmed else KARAOKE_QUERY_PREFIX + trimmed
            val queryHandler = service.searchQHFactory.fromQuery(karaokeQuery)
            SearchInfo
                .getInfo(service, queryHandler)
                .relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    Result(
                        videoId = extractVideoId(item.url),
                        title = formatVideoTitle(item.name.orEmpty()),
                        channelName = item.uploaderName.orEmpty(),
                        thumbnailUrl = selectThumbnailUrl(item.thumbnails),
                        durationSeconds = item.duration.takeIf { it in 0..Int.MAX_VALUE }?.toInt(),
                    )
                }
        } catch (e: Exception) {
            throw ApiException.UpstreamUnavailable("YouTube search failed: ${e.message}")
        }

    data class Result(
        val videoId: String,
        val title: String,
        val channelName: String,
        val thumbnailUrl: String?,
        val durationSeconds: Int?,
    )

    private fun selectThumbnailUrl(thumbnails: List<org.schabi.newpipe.extractor.Image>?): String? {
        if (thumbnails.isNullOrEmpty()) return null
        val minHeight = 90
        return thumbnails.filter { it.height >= minHeight }.minByOrNull { it.height }?.url
            ?: thumbnails.maxByOrNull { it.height }?.url
    }

    private fun extractVideoId(watchUrl: String): String {
        val marker = "v="
        val markerIndex = watchUrl.indexOf(marker)
        return if (markerIndex >= 0) {
            watchUrl.substring(markerIndex + marker.length).substringBefore('&')
        } else {
            watchUrl.substringAfterLast('/')
        }
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}

/** Duplicated from :youtube-client's NewPipeDownloaderAdapter — see this class's own doc for why. */
private class JvmDownloader(
    private val client: OkHttpClient,
) : Downloader() {
    override fun execute(request: Request): Response {
        val requestBody = request.dataToSend()?.toRequestBody()
        val builder =
            okhttp3.Request
                .Builder()
                .method(request.httpMethod(), requestBody)
                .url(request.url())
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; Karalo) AppleWebKit/537.36")
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCAPTCHA challenge requested", request.url())
            val body = response.body?.string().orEmpty()
            return Response(response.code, response.message, response.headers.toMultimap(), body, response.request.url.toString())
        }
    }
}
