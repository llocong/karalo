package com.karalo.youtubeclient.internal

import com.karalo.youtubeclient.model.YtStreamInfo
import com.karalo.youtubeclient.model.YtVideoSummary
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Maps NewPipeExtractor's result types onto our own DTOs so nothing outside this module needs to
 * know NewPipeExtractor's shapes (or survive its breaking changes directly).
 *
 * NOTE: field names (thumbnails, duration, resolution, content, mimeType) are based on
 * NewPipeExtractor's public API as of the pinned version in gradle/libs.versions.toml — verify
 * against that exact version during the integration spike (see docs/adr/0002 and README known
 * risks); these have changed across NewPipeExtractor releases before.
 */
internal object YtMapper {
    // Every consumer of thumbnailUrl in this app (confirmed: only FocusableCard's ~240dp-wide
    // shelf/search-result cards, via TvCarousel -- the Player screen carries this field through
    // as unused metadata, never renders it) displays a small card, never a large/full-screen
    // image. Picking the *smallest* available thumbnail that still clears a decode height with
    // comfortable margin for a focused (1.1x-scaled) card on a real TV avoids needlessly
    // downloading and decoding a much larger (often 720p+) image just to shrink it down --
    // real, measurable jank on a real (2GB RAM) reference TV, especially once TvCarousel's wider
    // cache window (see core-ui's TvCarousel.md) means many more cards are composed/prefetching
    // at once than before. Falls back to the largest available if every option is smaller than
    // this threshold, never worse than the previous always-largest behavior in that edge case.
    private const val MIN_THUMBNAIL_HEIGHT_PX = 320

    fun toVideoSummary(item: StreamInfoItem): YtVideoSummary =
        YtVideoSummary(
            videoId = extractVideoId(item.url),
            title = item.name.orEmpty(),
            channelName = item.uploaderName.orEmpty(),
            thumbnailUrl = item.thumbnails?.selectThumbnailUrl(),
            durationSeconds = item.duration.takeIf { it >= 0 },
        )

    private fun List<Image>.selectThumbnailUrl(): String? =
        filter { it.height >= MIN_THUMBNAIL_HEIGHT_PX }
            .minByOrNull { it.height }
            ?.url
            ?: maxByOrNull { it.height }?.url

    fun toStreamInfo(streamInfo: StreamInfo): YtStreamInfo? {
        // Prefer adaptive (DASH/HLS) over "progressive" (muxed) streams: YouTube's progressive
        // format entries are legacy and, since the SABR streaming rollout, are frequently listed
        // as available but no longer actually contain an audio track — silent video playback.
        // Adaptive manifests correctly reference separate, real audio and video renditions.
        streamInfo.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { dashUrl ->
            return YtStreamInfo(playbackUrl = dashUrl, mimeType = "application/dash+xml", isAdaptive = true)
        }

        streamInfo.hlsUrl?.takeIf { it.isNotBlank() }?.let { hlsUrl ->
            return YtStreamInfo(playbackUrl = hlsUrl, mimeType = "application/x-mpegURL", isAdaptive = true)
        }

        val progressive =
            StreamSelection.selectBest(
                candidates = streamInfo.videoStreams.orEmpty(),
                resolutionP = { parseResolutionP(it.resolution) },
            )
        if (progressive != null) {
            return YtStreamInfo(
                playbackUrl = progressive.content,
                mimeType = progressive.format?.mimeType,
                isAdaptive = false,
            )
        }

        return null
    }

    internal fun extractVideoId(watchUrl: String): String {
        val marker = "v="
        val markerIndex = watchUrl.indexOf(marker)
        return if (markerIndex >= 0) {
            watchUrl.substring(markerIndex + marker.length).substringBefore('&')
        } else {
            watchUrl.substringAfterLast('/')
        }
    }

    internal fun parseResolutionP(resolution: String?): Int? =
        resolution?.substringBefore('p')?.filter(Char::isDigit)?.toIntOrNull()
}
