package com.karalo.feature.player.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.karalo.core.common.error.AppError
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.mediakeys.MediaKeyRouter
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.karaoke.domain.KaraokeEvent
import com.karalo.core.karaoke.domain.KaraokeQueueSnapshot
import com.karalo.core.karaoke.domain.KaraokeSessionHolder
import com.karalo.core.karaoke.domain.NowPlaying
import com.karalo.core.karaoke.domain.NowPlayingSource
import com.karalo.core.karaoke.domain.QueueItem
import com.karalo.core.karaoke.domain.RemoteCommandType
import com.karalo.core.testing.FakeKaraokeRepository
import com.karalo.core.testing.MainDispatcherExtension
import com.karalo.feature.player.domain.ResolveStreamUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
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
    private val karaokeRepository = FakeKaraokeRepository()
    private val karaokeSessionHolder = mockk<KaraokeSessionHolder>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val listenerSlot = slot<Player.Listener>()

    // Shares mainDispatcherExtension's TestDispatcher/scheduler so a test's own advanceUntilIdle()
    // also drives work PlayerViewModel launches on this "app-wide" scope (see its onCleared()).
    private val appScope = CoroutineScope(mainDispatcherExtension.testDispatcher)

    private fun createViewModel(
        startIndex: Int = 0,
        startVideoId: String? = "vid1",
        sessionItems: List<PlayableItemRef> = emptyList(),
    ): PlayerViewModel {
        every { sessionHolder.getLastResults() } returns sessionItems
        coEvery { resolveStream(any()) } returns AppResult.Failure(AppError.NotFound)
        every { exoPlayer.addListener(capture(listenerSlot)) } answers {}

        val state =
            buildMap<String, Any?> {
                put("startIndex", startIndex)
                if (startVideoId != null) put("startVideoId", startVideoId)
            }
        return PlayerViewModel(
            SavedStateHandle(state),
            exoPlayer,
            resolveStream,
            sessionHolder,
            mediaKeyRouter,
            karaokeRepository,
            karaokeSessionHolder,
            logger,
            appScope,
        )
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
        }

    @Test
    fun `next reuses onPlaybackEnded to advance the persistent queue, same as a remote Skip`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val nextItem =
                NowPlaying(NowPlayingSource.QUEUE, "q2", "nextVid", "Next Song", "Chan", null, null, "Bob")
            karaokeRepository.consumeNextResult = AppResult.Success(nextItem)
            val viewModel = createQueueViewModel()
            advanceUntilIdle()

            viewModel.next()
            advanceUntilIdle()

            assertEquals(1, karaokeRepository.consumeNextCallCount)
            assertEquals(
                "nextVid",
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

    /**
     * Starts [startVideoId] as the persistent queue's own now-playing item (the auto-navigate
     * path), not a manual Play-Now selection -- so ending/skipping it goes through consumeNext.
     */
    private fun createQueueViewModel(startVideoId: String = "vid1"): PlayerViewModel {
        karaokeRepository.setQueueSnapshot(
            KaraokeQueueSnapshot(
                playbackState = "PLAYING",
                nowPlaying = NowPlaying(NowPlayingSource.QUEUE, "q1", startVideoId, "T", "C", null, null, "Alice"),
                queue = emptyList(),
            ),
        )
        return createViewModel(startVideoId = startVideoId, sessionItems = emptyList())
    }

    // --- Karaoke remote-control behavior ---------------------------------------------------

    @Test
    fun `manual Play Now selection never calls consumeNext or touches the persistent queue`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            // Nothing is playing from the persistent queue -- this is a genuine manual selection.
            karaokeRepository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)

            createViewModel(startVideoId = "manualVid1", sessionItems = emptyList())
            advanceUntilIdle()

            assertEquals(0, karaokeRepository.consumeNextCallCount)
            assertEquals("manualVid1", karaokeRepository.lastPlayNowStartVideoId)
        }

    @Test
    fun `starting exactly the persistent queue's own now-playing item is not treated as Play Now`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            // Simulates the auto-navigate path: the item being loaded IS the queue's current head.
            karaokeRepository.setQueueSnapshot(
                KaraokeQueueSnapshot(
                    playbackState = "PLAYING",
                    nowPlaying =
                        NowPlaying(
                            source = NowPlayingSource.QUEUE,
                            queueItemId = "q1",
                            videoId = "queueVid1",
                            title = "T",
                            channelName = "C",
                            thumbnailUrl = null,
                            durationSeconds = null,
                            addedByDisplayName = "Alice",
                        ),
                    queue = emptyList(),
                ),
            )

            createViewModel(startVideoId = "queueVid1", sessionItems = emptyList())
            advanceUntilIdle()

            assertEquals(null, karaokeRepository.lastPlayNowStartVideoId)
        }

    @Test
    fun `playback ending with a next persistent queue item plays it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val nextItem =
                NowPlaying(NowPlayingSource.QUEUE, "q2", "nextVid", "Next Song", "Chan", null, null, "Bob")
            karaokeRepository.consumeNextResult = AppResult.Success(nextItem)

            val viewModel = createQueueViewModel()
            advanceUntilIdle()

            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            assertEquals(1, karaokeRepository.consumeNextCallCount)
            assertEquals(
                "nextVid",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
            assertFalse(viewModel.uiState.value.isWaitingForQueue)
        }

    @Test
    fun `playback ending with an empty persistent queue shows the waiting screen`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            karaokeRepository.consumeNextResult = AppResult.Success(null)

            val viewModel = createQueueViewModel()
            advanceUntilIdle()

            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isWaitingForQueue)
            assertFalse(viewModel.uiState.value.isPlaying)
        }

    @Test
    fun `a consumeNext failure also falls back to the waiting screen rather than stranding the TV`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            karaokeRepository.consumeNextResult = AppResult.Failure(AppError.Network())

            val viewModel = createQueueViewModel()
            advanceUntilIdle()

            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isWaitingForQueue)
        }

    @Test
    fun `a Play Now song ending resumes the persistent queue at its head without consuming it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            // Regression: a phone added a song while a manual Play-Now selection was playing. Ending
            // Play-Now already hands back that song as now-playing; consuming on top of it used to
            // mark it played before it ever played and drop the TV onto the waiting screen.
            val queuedSong =
                NowPlaying(NowPlayingSource.QUEUE, "q2", "queuedVid", "Queued Song", "Chan", null, null, "Bob")
            karaokeRepository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)
            karaokeRepository.playNowEndResult = AppResult.Success(queuedSong)
            val viewModel = createViewModel(startVideoId = "manualVid1", sessionItems = emptyList())
            advanceUntilIdle()

            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            assertEquals(1, karaokeRepository.playNowEndCallCount)
            assertEquals(0, karaokeRepository.consumeNextCallCount)
            assertEquals(
                "queuedVid",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
            assertFalse(viewModel.uiState.value.isWaitingForQueue)
        }

    @Test
    fun `pressing Next during Play Now plays the queued song instead of skipping it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val queuedSong =
                NowPlaying(NowPlayingSource.QUEUE, "q2", "queuedVid", "Queued Song", "Chan", null, null, "Bob")
            karaokeRepository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)
            karaokeRepository.playNowEndResult = AppResult.Success(queuedSong)
            val viewModel = createViewModel(startVideoId = "manualVid1", sessionItems = emptyList())
            advanceUntilIdle()

            viewModel.next()
            advanceUntilIdle()

            assertEquals(0, karaokeRepository.consumeNextCallCount)
            assertEquals(
                "queuedVid",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
        }

    @Test
    fun `a Play Now song ending with an empty persistent queue shows the waiting screen`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            karaokeRepository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)
            karaokeRepository.playNowEndResult = AppResult.Success(null)
            val viewModel = createViewModel(startVideoId = "manualVid1", sessionItems = emptyList())
            advanceUntilIdle()

            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            assertEquals(0, karaokeRepository.consumeNextCallCount)
            assertTrue(viewModel.uiState.value.isWaitingForQueue)
        }

    @Test
    fun `a playNowEnd failure falls back to the waiting screen rather than stranding the TV`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            karaokeRepository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)
            karaokeRepository.playNowEndResult = AppResult.Failure(AppError.Network())
            val viewModel = createViewModel(startVideoId = "manualVid1", sessionItems = emptyList())
            advanceUntilIdle()

            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isWaitingForQueue)
        }

    @Test
    fun `a queue item arriving while waiting auto-clears the waiting screen and starts playing it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            karaokeRepository.consumeNextResult = AppResult.Success(null)
            val viewModel = createQueueViewModel()
            advanceUntilIdle()
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isWaitingForQueue)

            karaokeRepository.setQueueSnapshot(
                KaraokeQueueSnapshot(
                    playbackState = "PLAYING",
                    nowPlaying =
                        NowPlaying(NowPlayingSource.QUEUE, "q3", "arrivedVid", "Song", "Chan", null, null, "Cara"),
                    queue = emptyList(),
                ),
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isWaitingForQueue)
            assertEquals(
                "arrivedVid",
                viewModel.uiState.value.currentItem
                    ?.videoId,
            )
        }

    @Test
    fun `onCleared marks the player as no longer on screen`() {
        val viewModel = createViewModel()
        every { karaokeSessionHolder.isPlayerOnScreen = any() } answers {}

        invokeOnCleared(viewModel)

        verify { karaokeSessionHolder.isPlayerOnScreen = false }
    }

    @Test
    fun `leaving a Play Now session before it ends still ends it on the backend`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            // A genuine manual selection (see the first karaoke test above) -- isPlayNowActive
            // becomes true, but the video is abandoned (e.g. BACK) before STATE_ENDED ever fires.
            karaokeRepository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)
            val viewModel = createViewModel(startVideoId = "manualVid1", sessionItems = emptyList())
            every { karaokeSessionHolder.isPlayerOnScreen = any() } answers {}
            advanceUntilIdle()

            invokeOnCleared(viewModel)
            advanceUntilIdle()

            assertEquals(1, karaokeRepository.playNowEndCallCount)
        }

    @Test
    fun `a remote PAUSE command pauses the exoplayer`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            karaokeRepository.emitEvent(KaraokeEvent.RemoteCommand(RemoteCommandType.PAUSE, "Alice"))
            advanceUntilIdle()

            verify { exoPlayer.pause() }
        }

    @Test
    fun `a remote RESUME command resumes the exoplayer`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            karaokeRepository.emitEvent(KaraokeEvent.RemoteCommand(RemoteCommandType.RESUME, "Alice"))
            advanceUntilIdle()

            verify { exoPlayer.play() }
        }

    @Test
    fun `a remote SKIP command advances the persistent queue same as natural completion`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            karaokeRepository.consumeNextResult = AppResult.Success(null)
            createQueueViewModel()
            advanceUntilIdle()

            karaokeRepository.emitEvent(KaraokeEvent.RemoteCommand(RemoteCommandType.SKIP, "Alice"))
            advanceUntilIdle()

            assertEquals(1, karaokeRepository.consumeNextCallCount)
        }

    @Test
    fun `playback state changes are reported to the backend`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            listenerSlot.captured.onIsPlayingChanged(false)
            advanceUntilIdle()

            assertEquals(false, karaokeRepository.lastReportedIsPlaying)
        }

    @Test
    fun `hasNextInQueue is false when the persistent queue starts out empty`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            karaokeRepository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasNextInQueue)
        }

    @Test
    fun `hasNextInQueue becomes true once the persistent queue has a pending item`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.hasNextInQueue)

            karaokeRepository.setQueueSnapshot(
                KaraokeQueueSnapshot(
                    playbackState = "PLAYING",
                    nowPlaying = null,
                    queue =
                        listOf(
                            QueueItem(
                                id = "q1",
                                position = 0,
                                videoId = "nextVid",
                                title = "T",
                                channelName = "C",
                                thumbnailUrl = null,
                                durationSeconds = null,
                                addedByParticipantId = "p1",
                                addedByDisplayName = "Alice",
                                addedAt = "2024-01-01T00:00:00Z",
                            ),
                        ),
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasNextInQueue)
        }
}

/** [androidx.lifecycle.ViewModel.onCleared] is protected -- this small reflection shim is the
 *  established way to invoke it directly from a JVM unit test without a full ViewModelStore. */
private fun invokeOnCleared(viewModel: PlayerViewModel) {
    val method = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
    method.isAccessible = true
    method.invoke(viewModel)
}
