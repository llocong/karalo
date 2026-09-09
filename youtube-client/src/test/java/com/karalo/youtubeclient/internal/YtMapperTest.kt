package com.karalo.youtubeclient.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class YtMapperTest {

    @ParameterizedTest
    @CsvSource(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=abc, dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ, dQw4w9WgXcQ",
    )
    fun `extractVideoId pulls the video id from common URL shapes`(url: String, expectedId: String) {
        assertEquals(expectedId, YtMapper.extractVideoId(url))
    }

    @ParameterizedTest
    @CsvSource(
        "720p, 720",
        "1080p60, 1080",
        "144p, 144",
    )
    fun `parseResolutionP extracts the numeric resolution`(resolution: String, expected: Int) {
        assertEquals(expected, YtMapper.parseResolutionP(resolution))
    }

    @org.junit.jupiter.api.Test
    fun `parseResolutionP returns null for an unparsable or missing resolution`() {
        assertNull(YtMapper.parseResolutionP("unknown"))
        assertNull(YtMapper.parseResolutionP(null))
    }
}
