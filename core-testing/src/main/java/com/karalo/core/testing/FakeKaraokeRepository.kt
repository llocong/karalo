package com.karalo.core.testing

import com.karalo.core.common.error.AppError
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.common.result.AppResult
import com.karalo.core.karaoke.domain.HistoryPage
import com.karalo.core.karaoke.domain.HistoryPlay
import com.karalo.core.karaoke.domain.KaraokeEvent
import com.karalo.core.karaoke.domain.KaraokeQueueSnapshot
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.core.karaoke.domain.KaraokeSession
import com.karalo.core.karaoke.domain.MostPlayedSong
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

    /** In-memory song history, newest first; paged and aggregated like the backend does. */
    val historyPlays: MutableList<HistoryPlay> = mutableListOf()
    var historyPaused: Boolean = false

    /** When set, every history call fails with this error instead. */
    var historyError: AppError? = null
    var historyRequestCount: Int = 0

    private val queueSnapshotFlow = MutableStateFlow(KaraokeQueueSnapshot.EMPTY)
    override val queueSnapshot: StateFlow<KaraokeQueueSnapshot> = queueSnapshotFlow.asStateFlow()

    private val eventsFlow = MutableSharedFlow<KaraokeEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<KaraokeEvent> = eventsFlow.asSharedFlow()

    private val sessionJoinUrlFlow = MutableStateFlow<String?>(null)
    override val sessionJoinUrl: StateFlow<String?> = sessionJoinUrlFlow.asStateFlow()

    private val seasonalThemeFlow = MutableStateFlow(SeasonalTheme.DEFAULT)
    override val seasonalTheme: StateFlow<SeasonalTheme> = seasonalThemeFlow.asStateFlow()

    /** When set, [setSeasonalTheme] fails with this error and leaves the theme unchanged. */
    var setSeasonalThemeError: AppError? = null

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

    override suspend fun historyByDate(
        before: String?,
        limit: Int,
    ): AppResult<HistoryPage<HistoryPlay, String>> {
        historyRequestCount++
        historyError?.let { return AppResult.Failure(it) }
        val start = before?.toInt() ?: 0
        val end = minOf(start + limit, historyPlays.size)
        val next = if (end < historyPlays.size) end.toString() else null
        return AppResult.Success(HistoryPage(historyPlays.subList(start, end).toList(), next, historyPaused))
    }

    override suspend fun mostPlayed(
        offset: Int,
        limit: Int,
    ): AppResult<HistoryPage<MostPlayedSong, Int>> {
        historyRequestCount++
        historyError?.let { return AppResult.Failure(it) }
        val songs =
            historyPlays
                .groupBy { it.videoId }
                .map { (_, plays) ->
                    MostPlayedSong(
                        plays[0].videoId,
                        plays[0].title,
                        plays[0].channelName,
                        plays[0].thumbnailUrl,
                        plays.size,
                    )
                }.sortedByDescending { it.playCount }
        val end = minOf(offset + limit, songs.size)
        return AppResult.Success(HistoryPage(songs.subList(offset, end), end.takeIf { it < songs.size }, historyPaused))
    }

    override suspend fun setHistoryPaused(paused: Boolean): AppResult<Boolean> {
        historyError?.let { return AppResult.Failure(it) }
        historyPaused = paused
        return AppResult.Success(paused)
    }

    override suspend fun clearHistory(): AppResult<Unit> {
        historyError?.let { return AppResult.Failure(it) }
        historyPlays.clear()
        return AppResult.Success(Unit)
    }

    override suspend fun setSeasonalTheme(theme: SeasonalTheme): AppResult<SeasonalTheme> {
        setSeasonalThemeError?.let { return AppResult.Failure(it) }
        seasonalThemeFlow.value = theme
        return AppResult.Success(theme)
    }
}
