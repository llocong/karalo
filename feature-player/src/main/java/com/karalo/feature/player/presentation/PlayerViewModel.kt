package com.karalo.feature.player.presentation

import android.view.KeyEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.karalo.core.common.di.ApplicationScope
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.mediakeys.MediaKeyHandler
import com.karalo.core.common.mediakeys.MediaKeyRouter
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.karaoke.domain.KaraokeEvent
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.core.karaoke.domain.KaraokeSessionHolder
import com.karalo.core.karaoke.domain.NowPlaying
import com.karalo.core.karaoke.domain.PlayNowSong
import com.karalo.core.karaoke.domain.RemoteCommandType
import com.karalo.feature.player.domain.PlayableItem
import com.karalo.feature.player.domain.PlaybackQueue
import com.karalo.feature.player.domain.ResolveStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val GENERIC_PLAYBACK_ERROR = "Couldn't play this video."

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val exoPlayer: ExoPlayer,
        private val resolveStream: ResolveStreamUseCase,
        private val searchSessionHolder: SearchSessionHolder,
        private val mediaKeyRouter: MediaKeyRouter,
        private val karaokeRepository: KaraokeRepository,
        private val karaokeSessionHolder: KaraokeSessionHolder,
        private val logger: Logger,
        @ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        /**
         * Exposed as the read-only [Player] interface so the UI can attach a PlayerView / poll
         * position without controlling playback directly.
         */
        val player: Player get() = exoPlayer

        private val _uiState = MutableStateFlow(PlayerUiState())
        val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

        private var queue: PlaybackQueue = buildInitialQueue(savedStateHandle, searchSessionHolder)

        // True while the currently-loaded item is a TV-manual selection (Home/Search click, or the
        // process-death single-video fallback) rather than the persistent queue's own head starting
        // -- see the init block's disambiguation and this class's own doc for why the two look
        // identical by construction (both flow through the same SearchSessionHolder+nav mechanism)
        // and must be told apart by comparing videoId against the karaoke repository's own
        // nowPlaying at load time.
        private var isPlayNowActive: Boolean = false

        private val playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                    // Fire-and-forget: lets the phone's queue page reflect the TV's actual state
                    // (including a remote pause/resume command completing -- see the events
                    // collector below) regardless of whether this was a local or remote trigger.
                    viewModelScope.launch { karaokeRepository.reportPlaybackState(isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val isBuffering = playbackState == Player.STATE_BUFFERING
                    if (isBuffering) {
                        logger.recordEvent("playback_buffering", mapOf("videoId" to (queue.current?.videoId.orEmpty())))
                    }
                    _uiState.update { it.copy(isLoading = isBuffering) }
                    if (playbackState == Player.STATE_ENDED) onPlaybackEnded()
                }

                override fun onPlayerError(error: PlaybackException) {
                    logger.recordException(error)
                    _uiState.update { it.copy(isLoading = false, error = GENERIC_PLAYBACK_ERROR) }
                }
            }

        private val mediaKeyHandler = MediaKeyHandler { keyCode -> handleMediaKey(keyCode) }

        init {
            exoPlayer.addListener(playerListener)
            mediaKeyRouter.attach(mediaKeyHandler)
            // Marks Player as "on screen" for KaraokeSessionHolder's auto-navigate guard (see its
            // own doc) -- paired with onCleared() below. Not the primary guard (KaraloNavHost's own
            // isPlayerActive is), but narrows that race window further.
            karaokeSessionHolder.isPlayerOnScreen = true
            viewModelScope.launch {
                karaokeRepository.sessionJoinUrl.collect { url -> _uiState.update { it.copy(sessionJoinUrl = url) } }
            }
            // Drives the on-screen Next button's enabled state -- same rule the webapp's own Skip
            // button follows: nothing to skip to once the persistent queue is empty.
            viewModelScope.launch {
                karaokeRepository.queueSnapshot
                    .map { it.queue.isNotEmpty() }
                    .distinctUntilChanged()
                    .collect { hasNextInQueue -> _uiState.update { it.copy(hasNextInQueue = hasNextInQueue) } }
            }
            // Auto-clears the waiting screen the moment a phone adds a song while the TV is idle
            // there -- same consume/play path onPlaybackEnded uses, just triggered by a queue
            // arriving rather than the current item finishing.
            viewModelScope.launch {
                karaokeRepository.queueSnapshot
                    .map { it.nowPlaying }
                    .distinctUntilChanged()
                    .collect { nowPlaying ->
                        if (_uiState.value.isWaitingForQueue && nowPlaying != null) {
                            playFromNowPlaying(nowPlaying)
                        }
                    }
            }
            // A phone pressing Pause/Resume/Skip on the queue page relays here over the backend's
            // WebSocket -- see KaraokeRepositoryImpl's own WS message handling. Skip reuses
            // onPlaybackEnded()'s exact logic (consume-next, advance or show the waiting screen)
            // rather than duplicating it, since "the current song is over" is exactly what a
            // remote skip means for the persistent queue.
            viewModelScope.launch {
                karaokeRepository.events.collect { event ->
                    if (event is KaraokeEvent.RemoteCommand) {
                        when (event.command) {
                            RemoteCommandType.PAUSE -> exoPlayer.pause()
                            RemoteCommandType.RESUME -> exoPlayer.play()
                            RemoteCommandType.SKIP -> onPlaybackEnded()
                        }
                    }
                }
            }
            markPlayNowIfApplicable()
            playCurrent()
        }

        /**
         * Disambiguates "the TV user manually selected this" from "this is the persistent queue's
         * own head starting" -- both arrive here through the exact same mechanism (a 1-item list in
         * [SearchSessionHolder] + a nav-arg videoId, see [buildInitialQueue]), so the only reliable
         * signal is whether the loaded videoId matches what the karaoke repository already
         * considers now-playing at this exact moment: if [KaraokeSessionHolder] just auto-navigated
         * here because the persistent queue's head became playable, [karaokeRepository]'s own
         * `queueSnapshot.nowPlaying` already reflects that same item; a genuine manual click never
         * matches (Play-Now content isn't drawn from the persistent queue). Reported to the backend
         * with [KaraokeRepository.playNowStart] so the persistent queue's head stays untouched and
         * concurrent phone adds don't wrongly promote themselves to now-playing while this plays --
         * see this feature's spec section on Play Now preserving the queue.
         */
        private fun markPlayNowIfApplicable() {
            val item = queue.current ?: return
            val queueOriginated =
                item.videoId ==
                    karaokeRepository.queueSnapshot.value.nowPlaying
                        ?.videoId
            if (queueOriginated) return
            isPlayNowActive = true
            viewModelScope.launch {
                karaokeRepository.playNowStart(
                    PlayNowSong(item.videoId, item.title, item.channelName, item.thumbnailUrl, durationSeconds = null),
                )
            }
        }

        /**
         * The current video ended, OR the on-screen/remote Next-Skip button was pressed early --
         * both mean the same thing for the persistent remote queue: consume its next item (or show
         * the waiting screen if it has none) and play that, mirroring exactly what a phone's Skip
         * button does. [queue] (the *local*, ephemeral Play-Now/browse list built in
         * [buildInitialQueue]) plays no part in this -- see [next] for why that list no longer has
         * a browsing UI of its own.
         */
        private fun onPlaybackEnded() {
            viewModelScope.launch {
                // Ending a Play-Now song hands back the persistent queue's head *as* now-playing
                // (the backend's queue head is what's playing -- see its playNowEnd), so that head
                // is what plays next. Calling consumeNext on top of it would mark that head as
                // played before it ever played, skipping every song phones added during Play-Now.
                val result =
                    if (isPlayNowActive) {
                        isPlayNowActive = false
                        karaokeRepository.playNowEnd()
                    } else {
                        karaokeRepository.consumeNext()
                    }
                when (result) {
                    is AppResult.Success -> {
                        val nextItem = result.data
                        if (nextItem != null) {
                            playFromNowPlaying(nextItem)
                        } else {
                            _uiState.update { it.copy(isWaitingForQueue = true, isPlaying = false) }
                        }
                    }
                    is AppResult.Failure -> {
                        // A network hiccup here must not strand the TV silently on a frozen last
                        // frame -- fall back to the visible, recoverable waiting screen rather than
                        // doing nothing.
                        logger.log("Advancing the queue failed after playback ended: ${result.error}")
                        _uiState.update { it.copy(isWaitingForQueue = true, isPlaying = false) }
                    }
                }
            }
        }

        private fun playFromNowPlaying(nowPlaying: NowPlaying) {
            queue =
                PlaybackQueue.singleItem(
                    PlayableItem(nowPlaying.videoId, nowPlaying.title, nowPlaying.channelName, nowPlaying.thumbnailUrl),
                )
            playCurrent()
        }

        fun togglePlayPause() {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }

        /**
         * Called when the player screen's lifecycle stops (HOME press, switching to another app,
         * screen off) -- this ViewModel is nav-entry-scoped, not Activity-scoped, so it otherwise
         * has no way to know the app left the foreground and would keep playing audio unattended.
         */
        fun pausePlayback() {
            exoPlayer.pause()
        }

        /**
         * The on-screen/media-key Next button -- reuses [onPlaybackEnded] outright rather than
         * [queue]'s own local next/previous browsing (removed; there is no "Previous" concept in
         * the persistent remote queue), so pressing this has the exact same effect on the shared
         * queue as a phone pressing Skip: consume-next, then play that or show the waiting screen.
         */
        fun next() {
            onPlaybackEnded()
        }

        fun seekTo(positionMs: Long) {
            exoPlayer.seekTo(positionMs)
        }

        private fun handleMediaKey(keyCode: Int): Boolean {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> togglePlayPause()
                KeyEvent.KEYCODE_MEDIA_PLAY -> exoPlayer.play()
                KeyEvent.KEYCODE_MEDIA_PAUSE -> exoPlayer.pause()
                KeyEvent.KEYCODE_MEDIA_NEXT -> next()
                else -> return false
            }
            return true
        }

        private fun playCurrent() {
            val item =
                queue.current ?: run {
                    _uiState.update { it.copy(error = GENERIC_PLAYBACK_ERROR) }
                    return
                }
            _uiState.update {
                // .copy(), not a fresh PlayerUiState(...), specifically to preserve sessionJoinUrl
                // (a session-level constant, not a per-item field) across every song change --
                // reconstructing from scratch here would otherwise blank out the QR overlay for a
                // moment on every single track transition.
                it.copy(
                    currentItem = item,
                    isLoading = true,
                    isPlaying = false,
                    error = null,
                    isWaitingForQueue = false,
                )
            }
            viewModelScope.launch {
                when (val result = resolveStream(item.videoId)) {
                    is AppResult.Success -> {
                        val mediaItem =
                            MediaItem
                                .Builder()
                                .setUri(result.data.uri)
                                .apply { result.data.mimeType?.let(::setMimeType) }
                                .build()
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                    is AppResult.Failure -> {
                        logger.log("Stream resolution failed for ${item.videoId}: ${result.error}")
                        _uiState.update { it.copy(isLoading = false, error = GENERIC_PLAYBACK_ERROR) }
                    }
                }
            }
        }

        override fun onCleared() {
            karaokeSessionHolder.isPlayerOnScreen = false
            mediaKeyRouter.detach(mediaKeyHandler)
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
            // Leaving the player before the song ends naturally (BACK, or navigating away some
            // other way) never goes through onPlaybackEnded's own playNowEnd() call -- without this,
            // a Play-Now session abandoned mid-song would leave the backend's nowPlaying stuck on it
            // forever, permanently blocking the persistent queue from ever resuming. viewModelScope
            // is already cancelled by the time onCleared() runs, so this fires on the app-wide scope
            // instead -- it must outlive this ViewModel to actually reach the network.
            if (isPlayNowActive) {
                isPlayNowActive = false
                appScope.launch { karaokeRepository.playNowEnd() }
            }
        }
    }

/**
 * Picks which single item of the search screen's last results (left in [SearchSessionHolder]) to
 * start with, via the nav-arg start index -- falling back to a single-video queue using the
 * nav-arg videoId if the holder is empty (e.g. after process death). Nav-arg keys must match the
 * route defined in :app's NavDestinations.
 */
private fun buildInitialQueue(
    savedStateHandle: SavedStateHandle,
    searchSessionHolder: SearchSessionHolder,
): PlaybackQueue {
    val startIndex = savedStateHandle.get<Int>("startIndex") ?: 0
    val startVideoId = savedStateHandle.get<String>("startVideoId")
    val items = searchSessionHolder.getLastResults().map { it.toPlayableItem() }

    return when {
        items.isNotEmpty() -> PlaybackQueue(items, startIndex.coerceIn(items.indices))
        startVideoId != null ->
            PlaybackQueue.singleItem(
                PlayableItem(videoId = startVideoId, title = "", channelName = "", thumbnailUrl = null),
            )
        else -> PlaybackQueue.empty()
    }
}

private fun PlayableItemRef.toPlayableItem() = PlayableItem(videoId, title, channelName, thumbnailUrl)
