package com.karalo.core.karaoke.domain

import com.karalo.core.common.result.AppResult
import com.karalo.core.testing.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private fun nowPlaying(videoId: String) =
    NowPlaying(NowPlayingSource.QUEUE, "q-$videoId", videoId, "T", "C", null, null, "Alice")

/** Hand-written fake rather than MockK -- :core-karaoke can't depend on :core-testing's
 *  FakeKaraokeRepository (that module depends the other way, on :core-karaoke). */
private class FakeRepo : KaraokeRepository {
    val queueSnapshotFlow = MutableStateFlow(KaraokeQueueSnapshot.EMPTY)
    override val queueSnapshot: StateFlow<KaraokeQueueSnapshot> = queueSnapshotFlow.asStateFlow()
    val eventsFlow = MutableSharedFlow<KaraokeEvent>(extraBufferCapacity = 1)
    override val events: SharedFlow<KaraokeEvent> = eventsFlow
    override val sessionJoinUrl: StateFlow<String?> = MutableStateFlow(null)

    var consumeNextResult: AppResult<NowPlaying?> = AppResult.Success(null)
    var consumeNextCallCount = 0

    override suspend fun ensureSession(): AppResult<KaraokeSession> = error("unused")

    override suspend fun consumeNext(): AppResult<NowPlaying?> {
        consumeNextCallCount++
        return consumeNextResult
    }

    override suspend fun playNowStart(song: PlayNowSong): AppResult<NowPlaying?> = error("unused")

    override suspend fun playNowEnd(): AppResult<NowPlaying?> = error("unused")

    override suspend fun reportPlaybackState(isPlaying: Boolean): AppResult<Unit> = error("unused")
}

/**
 * Both the holder's own internal collector and the test's assertion collector are launched as
 * plain children of the test's own coroutine (not `TestScope.backgroundScope`) and explicitly
 * cancelled before the test ends -- empirically, `backgroundScope`'s coroutines here were not
 * being driven by this test's own `advanceUntilIdle()` calls (their side effects only appeared
 * well after the assertions had already run), so plain structured child coroutines -- which
 * `runTest`'s own scheduler unambiguously drives -- are used instead for reliable, synchronous
 * test control.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KaraokeSessionHolderTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    @Test
    fun `emits an auto-start request when now-playing transitions from null to non-null while off-screen`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val repo = FakeRepo()
            val holder = KaraokeSessionHolder(repo, this)
            holder.isPlayerOnScreen = false
            val emissions = mutableListOf<NowPlaying>()
            launch { holder.autoStartRequests.collect { emissions.add(it) } }
            advanceUntilIdle()

            repo.queueSnapshotFlow.value = KaraokeQueueSnapshot("PLAYING", nowPlaying("vid1"), emptyList())
            advanceUntilIdle()

            assertEquals(1, emissions.size)
            assertEquals("vid1", emissions.first().videoId)
            coroutineContext.cancelChildren()
        }

    @Test
    fun `does not emit while the player is already on screen`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val repo = FakeRepo()
            val holder = KaraokeSessionHolder(repo, this)
            holder.isPlayerOnScreen = true
            val emissions = mutableListOf<NowPlaying>()
            launch { holder.autoStartRequests.collect { emissions.add(it) } }
            advanceUntilIdle()

            repo.queueSnapshotFlow.value = KaraokeQueueSnapshot("PLAYING", nowPlaying("vid1"), emptyList())
            advanceUntilIdle()

            assertEquals(0, emissions.size)
            coroutineContext.cancelChildren()
        }

    @Test
    fun `a rapid duplicate now-playing update does not double-fire`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val repo = FakeRepo()
            val holder = KaraokeSessionHolder(repo, this)
            holder.isPlayerOnScreen = false
            val emissions = mutableListOf<NowPlaying>()
            launch { holder.autoStartRequests.collect { emissions.add(it) } }
            advanceUntilIdle()

            val snapshot = KaraokeQueueSnapshot("PLAYING", nowPlaying("vid1"), emptyList())
            repo.queueSnapshotFlow.value = snapshot
            advanceUntilIdle()
            // An identical (structurally-equal) snapshot -- e.g. a redundant WS reconcile -- must
            // not re-emit; distinctUntilChanged compares the whole data class by equals().
            repo.queueSnapshotFlow.value = snapshot.copy()
            advanceUntilIdle()

            assertEquals(1, emissions.size)
            coroutineContext.cancelChildren()
        }

    @Test
    fun `a remote RESUME command while off-screen re-emits the unchanged current now-playing`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val repo = FakeRepo()
            repo.queueSnapshotFlow.value = KaraokeQueueSnapshot("PLAYING", nowPlaying("vid1"), emptyList())
            val holder = KaraokeSessionHolder(repo, this)
            holder.isPlayerOnScreen = false
            val emissions = mutableListOf<NowPlaying>()
            launch { holder.autoStartRequests.collect { emissions.add(it) } }
            advanceUntilIdle()

            repo.eventsFlow.tryEmit(KaraokeEvent.RemoteCommand(RemoteCommandType.RESUME, "Alice"))
            advanceUntilIdle()

            assertEquals(1, emissions.size)
            assertEquals("vid1", emissions.first().videoId)
            coroutineContext.cancelChildren()
        }

    @Test
    fun `a remote SKIP command while off-screen consumes and starts the next queue item`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val repo = FakeRepo()
            repo.consumeNextResult = AppResult.Success(nowPlaying("vid2"))
            val holder = KaraokeSessionHolder(repo, this)
            holder.isPlayerOnScreen = false
            val emissions = mutableListOf<NowPlaying>()
            launch { holder.autoStartRequests.collect { emissions.add(it) } }
            advanceUntilIdle()

            repo.eventsFlow.tryEmit(KaraokeEvent.RemoteCommand(RemoteCommandType.SKIP, "Alice"))
            advanceUntilIdle()

            assertEquals(1, repo.consumeNextCallCount)
            assertEquals(1, emissions.size)
            assertEquals("vid2", emissions.first().videoId)
            coroutineContext.cancelChildren()
        }

    @Test
    fun `remote commands are ignored while the player is already on screen`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val repo = FakeRepo()
            repo.queueSnapshotFlow.value = KaraokeQueueSnapshot("PLAYING", nowPlaying("vid1"), emptyList())
            val holder = KaraokeSessionHolder(repo, this)
            holder.isPlayerOnScreen = true
            val emissions = mutableListOf<NowPlaying>()
            launch { holder.autoStartRequests.collect { emissions.add(it) } }
            advanceUntilIdle()

            repo.eventsFlow.tryEmit(KaraokeEvent.RemoteCommand(RemoteCommandType.RESUME, "Alice"))
            repo.eventsFlow.tryEmit(KaraokeEvent.RemoteCommand(RemoteCommandType.SKIP, "Alice"))
            advanceUntilIdle()

            assertEquals(0, repo.consumeNextCallCount)
            assertEquals(0, emissions.size)
            coroutineContext.cancelChildren()
        }
}
