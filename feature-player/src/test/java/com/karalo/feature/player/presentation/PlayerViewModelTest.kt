package com.karalo.feature.player.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.media3.exoplayer.ExoPlayer
import com.karalo.core.common.error.AppError
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.mediakeys.MediaKeyRouter
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.testing.MainDispatcherExtension
import com.karalo.feature.player.domain.ResolveStreamUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val exoPlayer = mockk<ExoPlayer>(relaxed = true)
    private val resolveStream = mockk<ResolveStreamUseCase>()
    private val sessionHolder = mockk<SearchSessionHolder>()
    private val mediaKeyRouter = mockk<MediaKeyRouter>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createViewModel(
        startIndex: Int = 0,
        startVideoId: String? = "vid1",
        sessionItems: List<PlayableItemRef> = emptyList(),
    ): PlayerViewModel {
        every { sessionHolder.getLastResults() } returns sessionItems
        coEvery { resolveStream(any()) } returns AppResult.Failure(AppError.NotFound)

        val state =
            buildMap<String, Any?> {
                put("startIndex", startIndex)
                if (startVideoId != null) put("startVideoId", startVideoId)
            }
        return PlayerViewModel(SavedStateHandle(state), exoPlayer, resolveStream, sessionHolder, mediaKeyRouter, logger)
    }

    @Test
    fun `falls back to a single-item queue from the nav arg when the session holder is empty`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel(startVideoId = "vid1", sessionItems = emptyList())
            advanceUntilIdle()

            assertEquals(
                "vid1",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
            assertFalse(viewModel.uiState.value.hasNext)
            assertFalse(viewModel.uiState.value.hasPrevious)
        }

    @Test
    fun `builds the queue from the search session and starts at the requested index`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val items =
                listOf(
                    PlayableItemRef("a", "A", "C", null, null),
                    PlayableItemRef("b", "B", "C", null, null),
                )

            val viewModel = createViewModel(startIndex = 1, sessionItems = items)
            advanceUntilIdle()

            assertEquals(
                "b",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
            assertTrue(viewModel.uiState.value.hasPrevious)
            assertFalse(viewModel.uiState.value.hasNext)
        }

    @Test
    fun `next resolves and plays the next item in the queue`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val items =
                listOf(
                    PlayableItemRef("a", "A", "C", null, null),
                    PlayableItemRef("b", "B", "C", null, null),
                )
            val viewModel = createViewModel(startIndex = 0, sessionItems = items)
            advanceUntilIdle()

            viewModel.next()
            advanceUntilIdle()

            assertEquals(
                "b",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
        }

    @Test
    fun `previous at the first item does not change the current item`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val items =
                listOf(
                    PlayableItemRef("a", "A", "C", null, null),
                    PlayableItemRef("b", "B", "C", null, null),
                )
            val viewModel = createViewModel(startIndex = 0, sessionItems = items)
            advanceUntilIdle()

            viewModel.previous()
            advanceUntilIdle()

            assertEquals(
                "a",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
        }

    @Test
    fun `togglePlayPause pauses the player when it is currently playing`() {
        every { exoPlayer.isPlaying } returns true
        val viewModel = createViewModel()

        viewModel.togglePlayPause()

        verify { exoPlayer.pause() }
    }

    @Test
    fun `togglePlayPause plays the player when it is currently paused`() {
        every { exoPlayer.isPlaying } returns false
        val viewModel = createViewModel()

        viewModel.togglePlayPause()

        verify { exoPlayer.play() }
    }

    @Test
    fun `attaches itself to the media key router on creation`() {
        createViewModel()

        verify { mediaKeyRouter.attach(any()) }
    }
}
