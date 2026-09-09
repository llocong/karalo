package com.karalo.feature.player.presentation

import android.view.KeyEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.mediakeys.MediaKeyHandler
import com.karalo.core.common.mediakeys.MediaKeyRouter
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.feature.player.domain.PlayableItem
import com.karalo.feature.player.domain.PlaybackQueue
import com.karalo.feature.player.domain.ResolveStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val GENERIC_PLAYBACK_ERROR = "Couldn't play this video."

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exoPlayer: ExoPlayer,
    private val resolveStream: ResolveStreamUseCase,
    private val searchSessionHolder: SearchSessionHolder,
    private val mediaKeyRouter: MediaKeyRouter,
    private val logger: Logger,
) : ViewModel() {

    /** Exposed as the read-only [Player] interface so the UI can attach a PlayerView / poll position without controlling playback directly. */
    val player: Player get() = exoPlayer

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var queue: PlaybackQueue = buildInitialQueue(savedStateHandle, searchSessionHolder)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            if (isBuffering) {
                logger.recordEvent("playback_buffering", mapOf("videoId" to (queue.current?.videoId.orEmpty())))
            }
            _uiState.update { it.copy(isLoading = isBuffering) }
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
        playCurrent()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun next() {
        if (!queue.hasNext) return
        queue = queue.next()
        playCurrent()
    }

    fun previous() {
        if (!queue.hasPrevious) return
        queue = queue.previous()
        playCurrent()
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
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> previous()
            else -> return false
        }
        return true
    }

    private fun playCurrent() {
        val item = queue.current ?: run {
            _uiState.update { it.copy(error = GENERIC_PLAYBACK_ERROR) }
            return
        }
        _uiState.update {
            PlayerUiState(
                currentItem = item,
                hasNext = queue.hasNext,
                hasPrevious = queue.hasPrevious,
                isLoading = true,
            )
        }
        viewModelScope.launch {
            when (val result = resolveStream(item.videoId)) {
                is AppResult.Success -> {
                    val mediaItem = MediaItem.Builder()
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
        mediaKeyRouter.detach(mediaKeyHandler)
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}

/**
 * Builds the starting queue from whatever the search screen left in [SearchSessionHolder], falling
 * back to a single-video queue (no Next/Previous) using the nav-arg videoId if the holder is empty
 * — e.g. after process death. Nav-arg keys must match the route defined in :app's NavDestinations.
 */
private fun buildInitialQueue(savedStateHandle: SavedStateHandle, searchSessionHolder: SearchSessionHolder): PlaybackQueue {
    val startIndex = savedStateHandle.get<Int>("startIndex") ?: 0
    val startVideoId = savedStateHandle.get<String>("startVideoId")
    val items = searchSessionHolder.getLastResults().map { it.toPlayableItem() }

    return when {
        items.isNotEmpty() -> PlaybackQueue(items, startIndex.coerceIn(items.indices))
        startVideoId != null -> PlaybackQueue.singleItem(
            PlayableItem(videoId = startVideoId, title = "", channelName = "", thumbnailUrl = null),
        )
        else -> PlaybackQueue.empty()
    }
}

private fun PlayableItemRef.toPlayableItem() = PlayableItem(videoId, title, channelName, thumbnailUrl)
