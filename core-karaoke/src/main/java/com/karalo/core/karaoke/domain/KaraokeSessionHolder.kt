package com.karalo.core.karaoke.domain

import com.karalo.core.common.di.ApplicationScope
import com.karalo.core.common.result.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks, at the app level (outside any nav-entry-scoped ViewModel), whether the TV is currently
 * "in Karaoke Mode" (Player is on screen) -- so that when the persistent remote queue's
 * now-playing transitions from nothing to something (a phone added the very first song, or the
 * queue resumed after the waiting screen) while nobody navigated there manually, something above
 * `KaraloNavHost`'s own `NavController` can react by navigating into Player, without
 * `KaraloNavHost` having to know anything about the karaoke backend itself.
 *
 * Deliberately keyed on [KaraokeRepository.queueSnapshot]'s `nowPlaying` field transitioning to
 * non-null (a `NOW_PLAYING_CHANGED` event, conceptually) rather than "the queue became non-empty":
 * the backend itself already promotes a freshly-added item to now-playing the instant nothing else
 * is playing (see the backend's `QueueRepository.add`), so by the time this holder's collector
 * would ever observe "queue non-empty", `nowPlaying` is already set too -- reacting to that same
 * signal directly is simpler and avoids a second, redundant condition.
 *
 * [isPlayerOnScreen] is flipped by `PlayerViewModel`'s own `init`/`onCleared` (see
 * `feature-player`) -- a secondary, defensive signal. The primary guard against a double-navigate
 * is `KaraloNavHost`'s own `isPlayerActive` (derived from the real NavHost back stack), checked at
 * the collection site; this field exists so this holder doesn't emit at all while Player is
 * already known to be mounted, narrowing the race window further.
 */
@Singleton
class KaraokeSessionHolder
    @Inject
    constructor(
        karaokeRepository: KaraokeRepository,
        @ApplicationScope appScope: CoroutineScope,
    ) {
        @Volatile
        var isPlayerOnScreen: Boolean = false

        private val autoStartRequestsFlow = MutableSharedFlow<NowPlaying>(extraBufferCapacity = 1)

        /** Emits once per nothing-playing -> something-playing transition observed while off-screen. */
        val autoStartRequests: SharedFlow<NowPlaying> = autoStartRequestsFlow.asSharedFlow()

        init {
            appScope.launch {
                // previousNowPlaying is only ever read/written from this single collector, so a
                // plain local var (no synchronization) is safe here.
                var previousNowPlaying: NowPlaying? = null
                var baselineTaken = false
                karaokeRepository.queueSnapshot
                    // The first snapshot loaded after launch is where things stand, not a change:
                    // a song left waiting in the queue must not start playing on its own the
                    // moment the app opens. Only transitions after it count.
                    .filter { it.loaded }
                    .map { it.nowPlaying }
                    .distinctUntilChanged()
                    .collect { nowPlaying ->
                        val previous = previousNowPlaying
                        previousNowPlaying = nowPlaying
                        if (!baselineTaken) {
                            baselineTaken = true
                            return@collect
                        }
                        // Must be a genuine nothing-playing -> something-playing transition
                        // (previous == null), not merely *some* change while off-screen -- e.g. a
                        // manually-selected Play-Now video ending (BACK) hands nowPlaying back to
                        // the persistent queue's own next item, which is a real (non-null ->
                        // non-null) change here too, but must NOT reopen Player: the TV user just
                        // chose to leave.
                        if (!isPlayerOnScreen && previous == null && nowPlaying != null) {
                            autoStartRequestsFlow.tryEmit(nowPlaying)
                        }
                    }
            }
            // A phone's Play/Resume or Next/Skip button while the TV is sitting on Home/Search/
            // Settings (no PlayerViewModel alive to react to these -- see its own identical
            // handling for the on-screen case) needs a different reaction than the queue-changed
            // collector above: nowPlaying may already be non-null and unchanged (e.g. the TV left
            // a still-playing QUEUE item via BACK), so that collector alone would never fire again.
            appScope.launch {
                karaokeRepository.events.collect { event ->
                    if (isPlayerOnScreen || event !is KaraokeEvent.RemoteCommand) return@collect
                    when (event.command) {
                        RemoteCommandType.RESUME ->
                            karaokeRepository.queueSnapshot.value.nowPlaying
                                ?.let { autoStartRequestsFlow.tryEmit(it) }
                        RemoteCommandType.SKIP -> {
                            val result = karaokeRepository.consumeNext()
                            if (result is AppResult.Success) result.data?.let { autoStartRequestsFlow.tryEmit(it) }
                        }
                        RemoteCommandType.PAUSE -> Unit // Nothing is actually playing off-screen to pause.
                    }
                }
            }
        }
    }
