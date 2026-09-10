package com.karalo.youtubeclient.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

private fun streamInfoItem(
    url: String = "https://www.youtube.com/watch?v=abc123",
    name: String? = "A Title",
    uploaderName: String? = "A Channel",
    duration: Long = 125,
    thumbnails: List<Image>? = null,
): StreamInfoItem =
    StreamInfoItem(0, url, name, StreamType.VIDEO_STREAM).apply {
        this.uploaderName = uploaderName
        this.duration = duration
        setThumbnails(thumbnails ?: emptyList())
    }

private fun streamInfo(
    dashMpdUrl: String? = null,
    hlsUrl: String? = null,
    videoStreams: List<VideoStream>? = null,
): StreamInfo =
    StreamInfo(0, "url", "originalUrl", StreamType.VIDEO_STREAM, "id", "name", 0).apply {
        this.dashMpdUrl = dashMpdUrl
        this.hlsUrl = hlsUrl
        this.videoStreams = videoStreams
    }

private fun videoStream(
    content: String,
    resolution: String,
    mediaFormat: MediaFormat = MediaFormat.MPEG_4,
): VideoStream =
    VideoStream
        .Builder()
        .setId(content)
        .setContent(content, true)
        .setMediaFormat(mediaFormat)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setResolution(resolution)
        .setIsVideoOnly(false)
        .build()

class YtMapperTest {
    @Test
    fun `toVideoSummary maps every field, preferring the largest thumbnail`() {
        val item =
            streamInfoItem(
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                name = "A Title",
                uploaderName = "A Channel",
                duration = 212,
                thumbnails =
                    listOf(
                        Image("small.jpg", 90, 120, Image.ResolutionLevel.LOW),
                        Image("large.jpg", 720, 1280, Image.ResolutionLevel.HIGH),
                    ),
            )

        val result = YtMapper.toVideoSummary(item)

        assertEquals("dQw4w9WgXcQ", result.videoId)
        assertEquals("A Title", result.title)
        assertEquals("A Channel", result.channelName)
        assertEquals("large.jpg", result.thumbnailUrl)
        assertEquals(212L, result.durationSeconds)
    }

    @Test
    fun `toVideoSummary treats a negative duration as unknown`() {
        val item = streamInfoItem(duration = -1)

        val result = YtMapper.toVideoSummary(item)

        assertNull(result.durationSeconds)
    }

    @Test
    fun `toVideoSummary defaults missing name, uploader and thumbnails`() {
        val item = streamInfoItem(name = null, uploaderName = null, thumbnails = null)

        val result = YtMapper.toVideoSummary(item)

        assertEquals("", result.title)
        assertEquals("", result.channelName)
        assertNull(result.thumbnailUrl)
    }

    @Test
    fun `toStreamInfo prefers a DASH manifest when present`() {
        val info = streamInfo(dashMpdUrl = "https://example.com/manifest.mpd")

        val result = YtMapper.toStreamInfo(info)

        assertEquals("https://example.com/manifest.mpd", result?.playbackUrl)
        assertEquals("application/dash+xml", result?.mimeType)
        assertEquals(true, result?.isAdaptive)
    }

    @Test
    fun `toStreamInfo falls back to HLS when there is no DASH manifest`() {
        val info = streamInfo(dashMpdUrl = null, hlsUrl = "https://example.com/playlist.m3u8")

        val result = YtMapper.toStreamInfo(info)

        assertEquals("https://example.com/playlist.m3u8", result?.playbackUrl)
        assertEquals("application/x-mpegURL", result?.mimeType)
        assertEquals(true, result?.isAdaptive)
    }

    @Test
    fun `toStreamInfo falls back to the best progressive stream when no adaptive manifest exists`() {
        val info =
            streamInfo(
                videoStreams =
                    listOf(
                        videoStream(content = "https://example.com/144p.mp4", resolution = "144p"),
                        videoStream(content = "https://example.com/720p.mp4", resolution = "720p"),
                    ),
            )

        val result = YtMapper.toStreamInfo(info)

        assertEquals("https://example.com/720p.mp4", result?.playbackUrl)
        assertEquals(MediaFormat.MPEG_4.mimeType, result?.mimeType)
        assertEquals(false, result?.isAdaptive)
    }

    @Test
    fun `toStreamInfo returns null when there is no playable stream at all`() {
        val info = streamInfo()

        val result = YtMapper.toStreamInfo(info)

        assertNull(result)
    }

    @ParameterizedTest
    @CsvSource(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=abc, dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ, dQw4w9WgXcQ",
    )
    fun `extractVideoId pulls the video id from common URL shapes`(
        url: String,
        expectedId: String,
    ) {
        assertEquals(expectedId, YtMapper.extractVideoId(url))
    }

    @ParameterizedTest
    @CsvSource(
        "720p, 720",
        "1080p60, 1080",
        "144p, 144",
    )
    fun `parseResolutionP extracts the numeric resolution`(
        resolution: String,
        expected: Int,
    ) {
        assertEquals(expected, YtMapper.parseResolutionP(resolution))
    }

    @org.junit.jupiter.api.Test
    fun `parseResolutionP returns null for an unparsable or missing resolution`() {
        assertNull(YtMapper.parseResolutionP("unknown"))
        assertNull(YtMapper.parseResolutionP(null))
    }
}
