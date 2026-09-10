package com.karalo.youtubeclient.internal

import com.karalo.youtubeclient.model.YtStreamInfo
import com.karalo.youtubeclient.model.YtVideoSummary
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
    fun toVideoSummary(item: StreamInfoItem): YtVideoSummary =
        YtVideoSummary(
            videoId = extractVideoId(item.url),
            title = item.name.orEmpty(),
            channelName = item.uploaderName.orEmpty(),
            thumbnailUrl = item.thumbnails?.maxByOrNull { it.height }?.url,
            durationSeconds = item.duration.takeIf { it >= 0 },
        )

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
