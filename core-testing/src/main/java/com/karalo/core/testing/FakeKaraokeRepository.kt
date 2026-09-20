package com.karalo.core.testing

import com.karalo.core.common.result.AppResult
import com.karalo.core.karaoke.domain.KaraokeEvent
import com.karalo.core.karaoke.domain.KaraokeQueueSnapshot
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.core.karaoke.domain.KaraokeSession
import com.karalo.core.karaoke.domain.NowPlaying
import com.karalo.core.karaoke.domain.PlayNowSong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Scriptable [KaraokeRepository] test double, same shape/spirit as [FakeYouTubeClient]. */
class FakeKaraokeRepository : KaraokeRepository {
    var ensureSessionResult: AppResult<KaraokeSession> =
        AppResult.Success(
            KaraokeSession(sessionId = "fake-session", sessionCode = "FAKE1234", joinUrl = "http://fake/join/FAKE1234"),
        )
    var consumeNextResult: AppResult<NowPlaying?> = AppResult.Success(null)
    var playNowStartResult: AppResult<NowPlaying?> = AppResult.Success(null)
    var playNowEndResult: AppResult<NowPlaying?> = AppResult.Success(null)

    var ensureSessionCallCount: Int = 0
    var consumeNextCallCount: Int = 0
    var lastPlayNowStartVideoId: String? = null
    var playNowEndCallCount: Int = 0
    var reportPlaybackStateResult: AppResult<Unit> = AppResult.Success(Unit)
    var lastReportedIsPlaying: Boolean? = null

    private val queueSnapshotFlow = MutableStateFlow(KaraokeQueueSnapshot.EMPTY)
    override val queueSnapshot: StateFlow<KaraokeQueueSnapshot> = queueSnapshotFlow.asStateFlow()

    private val eventsFlow = MutableSharedFlow<KaraokeEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<KaraokeEvent> = eventsFlow.asSharedFlow()

    private val sessionJoinUrlFlow = MutableStateFlow<String?>(null)
    override val sessionJoinUrl: StateFlow<String?> = sessionJoinUrlFlow.asStateFlow()

    /** Test-only setters — production code only ever observes these as read-only flows. */
    fun setQueueSnapshot(snapshot: KaraokeQueueSnapshot) {
        queueSnapshotFlow.value = snapshot
    }

    fun setSessionJoinUrl(url: String?) {
        sessionJoinUrlFlow.value = url
    }

    fun emitEvent(event: KaraokeEvent) {
        eventsFlow.tryEmit(event)
    }

    override suspend fun ensureSession(): AppResult<KaraokeSession> {
        ensureSessionCallCount++
        if (ensureSessionResult is AppResult.Success) {
            sessionJoinUrlFlow.value = (ensureSessionResult as AppResult.Success<KaraokeSession>).data.joinUrl
        }
        return ensureSessionResult
    }

    override suspend fun consumeNext(): AppResult<NowPlaying?> {
        consumeNextCallCount++
        return consumeNextResult
    }

    override suspend fun playNowStart(song: PlayNowSong): AppResult<NowPlaying?> {
        lastPlayNowStartVideoId = song.videoId
        return playNowStartResult
    }

    override suspend fun playNowEnd(): AppResult<NowPlaying?> {
        playNowEndCallCount++
        return playNowEndResult
    }

    override suspend fun reportPlaybackState(isPlaying: Boolean): AppResult<Unit> {
        lastReportedIsPlaying = isPlaying
        return reportPlaybackStateResult
    }
}
