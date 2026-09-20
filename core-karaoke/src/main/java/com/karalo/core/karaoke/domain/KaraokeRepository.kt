package com.karalo.core.karaoke.domain

import com.karalo.core.common.result.AppResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The TV's single window onto the backend-owned karaoke session/queue — deliberately parallel in
 * spirit to `:youtube-client`'s `YouTubeClient` ("the app's only window into YouTube"), but for the
 * remote-control feature's backend instead. Both `:app` (the auto-navigate-to-Player trigger) and
 * `:feature-player` (the QR overlay, waiting screen, and auto-advance-on-completion) depend on
 * this same instance, which is why it lives in this shared module rather than either of theirs.
 *
 * [queueSnapshot] and [events] are `@Singleton`-scoped (not tied to any nav-entry/ViewModel) since
 * the persistent queue must keep being observed independent of whether Player is even on screen --
 * see this module's own ADR for why this is a genuinely separate concern from
 * `:feature-player`'s existing, per-nav-entry, ephemeral `PlaybackQueue`.
 */
interface KaraokeRepository {
    /** Idempotent -- safe (and required) to call on every app launch; never creates a duplicate session. */
    suspend fun ensureSession(): AppResult<KaraokeSession>

    /** The currently-playing QUEUE item finished or was skipped -- advances the persistent queue. */
    suspend fun consumeNext(): AppResult<NowPlaying?>

    /**
     * Reports the TV's own manual Home/Search selection to the backend, marking now-playing as
     * PLAY_NOW -- the persistent queue's head is left completely untouched (see
     * `syncNowPlayingToQueueHead`'s doc on the backend) so it resumes exactly where it was once
     * [playNowEnd] is called.
     */
    suspend fun playNowStart(song: PlayNowSong): AppResult<NowPlaying?>

    suspend fun playNowEnd(): AppResult<NowPlaying?>

    /** Reports the TV's actual ExoPlayer play/pause state, so phones' queue page reflects reality. */
    suspend fun reportPlaybackState(isPlaying: Boolean): AppResult<Unit>

    /** Latest known snapshot, kept current by realtime events + periodic/reconnect REST reconcile. */
    val queueSnapshot: StateFlow<KaraokeQueueSnapshot>

    /** One-shot events (participant presence, remote pause/resume/skip commands) -- see [KaraokeEvent]. */
    val events: SharedFlow<KaraokeEvent>

    /** The known join URL once the session is established, for the QR placements. Null until then. */
    val sessionJoinUrl: StateFlow<String?>
}
