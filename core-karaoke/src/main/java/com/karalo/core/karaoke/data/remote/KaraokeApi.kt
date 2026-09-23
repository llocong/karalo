package com.karalo.core.karaoke.data.remote

import com.karalo.core.common.result.AppResult
import com.karalo.core.karaoke.data.remote.dto.NowPlayingPayloadDto
import com.karalo.core.karaoke.data.remote.dto.QueueSnapshotDto
import com.karalo.core.karaoke.data.remote.dto.SessionEnsureResponseDto
import com.karalo.core.karaoke.domain.PlayNowSong

/**
 * Thin wrapper around the karaoke backend's REST API — hand-rolled OkHttp + kotlinx.serialization
 * rather than Retrofit, matching this repo's existing "wrap the underlying library directly, no
 * heavyweight abstraction" style (see `:core-network`'s bare `OkHttpClient`,
 * `PlaybackRepositoryImpl`), and proportionate to the small (3-call) surface the TV actually needs.
 */
interface KaraokeApi {
    suspend fun ensureSession(
        tvInstallationId: String,
        tvSecret: String?,
    ): AppResult<SessionEnsureResponseDto>

    suspend fun fetchQueue(
        sessionId: String,
        tvSecret: String,
    ): AppResult<QueueSnapshotDto>

    suspend fun consumeNext(
        sessionId: String,
        tvSecret: String,
    ): AppResult<NowPlayingPayloadDto>

    suspend fun playNowStart(
        sessionId: String,
        tvSecret: String,
        song: PlayNowSong,
    ): AppResult<NowPlayingPayloadDto>

    suspend fun playNowEnd(
        sessionId: String,
        tvSecret: String,
    ): AppResult<NowPlayingPayloadDto>

    /** Reports the TV's actual ExoPlayer play/pause state, so phones' queue page reflects reality. */
    suspend fun reportPlaybackState(
        sessionId: String,
        tvSecret: String,
        isPlaying: Boolean,
    ): AppResult<Unit>
}
