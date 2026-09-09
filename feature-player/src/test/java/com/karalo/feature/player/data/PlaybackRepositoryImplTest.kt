package com.karalo.feature.player.data

import com.karalo.core.common.result.AppResult
import com.karalo.core.testing.FakeYouTubeClient
import com.karalo.feature.player.domain.PlayableStream
import com.karalo.youtubeclient.model.YtStreamInfo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackRepositoryImplTest {
    private val fakeClient = FakeYouTubeClient()
    private val repository = PlaybackRepositoryImpl(fakeClient)

    @Test
    fun `resolveStream maps the YouTube client's stream info onto the domain model`() =
        runTest {
            fakeClient.streamResult =
                AppResult.Success(
                    YtStreamInfo(playbackUrl = "https://example/video.mp4", mimeType = "video/mp4", isAdaptive = false),
                )

            val result = repository.resolveStream("abc123")

            assertEquals(
                AppResult.Success(PlayableStream("https://example/video.mp4", "video/mp4", false)),
                result,
            )
            assertEquals("abc123", fakeClient.lastStreamVideoId)
        }
}
