package com.karalo.feature.player.presentation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.ErrorState
import com.karalo.core.ui.components.KaraokeQrCode
import com.karalo.core.ui.components.LoadingIndicator
import kotlinx.coroutines.delay

private const val PROGRESS_POLL_INTERVAL_MS = 500L

private const val CONTROLS_AUTO_HIDE_MS = 3000L

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var revealFocusTarget by remember { mutableStateOf(RevealFocusTarget.PLAY_PAUSE) }
    val rootFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val seekFocusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        while (true) {
            val player = viewModel.player
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(PROGRESS_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(controlsVisible, uiState.isPlaying, interactionTick) {
        if (controlsVisible && uiState.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            when (revealFocusTarget) {
                RevealFocusTarget.PLAY_PAUSE -> playFocusRequester.requestFocus()
                RevealFocusTarget.SEEK_BAR -> seekFocusRequester.requestFocus()
            }
        }
    }

    // This screen's ExoPlayer is nav-entry-scoped (see PlayerViewModel), not tied to the
    // Activity, so it otherwise keeps playing audio after HOME/backgrounding/switching apps --
    // pausing on ON_STOP is what actually stops it in those cases.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.pausePlayback()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    when (classifyPlayerKeyEvent(keyEvent, controlsVisible)) {
                        PlayerKeyAction.REVEAL_CONTROLS -> {
                            interactionTick++
                            revealFocusTarget = RevealFocusTarget.PLAY_PAUSE
                            controlsVisible = true
                            true
                        }
                        PlayerKeyAction.REVEAL_CONTROLS_ON_SEEK_BAR -> {
                            interactionTick++
                            revealFocusTarget = RevealFocusTarget.SEEK_BAR
                            controlsVisible = true
                            true
                        }
                        PlayerKeyAction.HIDE_CONTROLS -> {
                            controlsVisible = false
                            true
                        }
                        PlayerKeyAction.RESET_AUTO_HIDE_TIMER -> {
                            interactionTick++
                            false
                        }
                        PlayerKeyAction.IGNORE -> false
                    }
                },
    ) {
        val context = LocalContext.current
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    useController = false
                    player = viewModel.player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        PlayerOverlays(
            uiState = uiState,
            controlsVisible = controlsVisible,
            positionMs = positionMs,
            durationMs = durationMs,
            playFocusRequester = playFocusRequester,
            seekFocusRequester = seekFocusRequester,
            onPlayPauseClick = {
                controlsVisible = true
                viewModel.togglePlayPause()
            },
            onNextClick = {
                controlsVisible = true
                viewModel.next()
            },
            onSeek = viewModel::seekTo,
            onHideControls = { controlsVisible = false },
        )
    }
}

/**
 * Everything drawn on top of the raw video surface -- split out of [PlayerScreen] purely to keep
 * that function's own branching (key handling, lifecycle, progress polling) from compounding with
 * this content's own (error/loading/QR/waiting-screen/controls), not because this content is
 * reused anywhere else.
 */
@Composable
private fun BoxScope.PlayerOverlays(
    uiState: PlayerUiState,
    controlsVisible: Boolean,
    positionMs: Long,
    durationMs: Long,
    playFocusRequester: FocusRequester,
    seekFocusRequester: FocusRequester,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onHideControls: () -> Unit,
) {
    when {
        uiState.error != null -> ErrorState(message = uiState.error.orEmpty(), modifier = Modifier.fillMaxSize())
        uiState.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
    }

    // Persistent, static, high-contrast -- shown throughout Karaoke Mode regardless of the
    // controls' own show/hide state (no timeout, per this feature's spec), always at the same
    // bottom-left spot the waiting screen below also uses. Mutually exclusive with the waiting
    // screen's own QR instance rather than layering both, avoiding a redundant second bitmap
    // decode for the same content. Same fixed size regardless of play/pause state -- no
    // playing-specific exception here, matching every other spot this QR appears.
    if (uiState.sessionJoinUrl != null && !uiState.isWaitingForQueue) {
        KaraokeQrCode(
            content = uiState.sessionJoinUrl,
            sizeDp = KARAOKE_QR_SIZE,
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp),
        )
    }

    if (uiState.isWaitingForQueue) {
        KaraokeWaitingScreen(sessionJoinUrl = uiState.sessionJoinUrl, modifier = Modifier.fillMaxSize())
    }

    if (controlsVisible && !uiState.isWaitingForQueue) {
        PlayerControlsOverlay(
            title = formatVideoTitle(uiState.currentItem?.title.orEmpty()),
            isPlaying = uiState.isPlaying,
            hasNextInQueue = uiState.hasNextInQueue,
            positionMs = positionMs,
            durationMs = durationMs,
            playFocusRequester = playFocusRequester,
            seekFocusRequester = seekFocusRequester,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = onNextClick,
            onSeek = onSeek,
            onHideControls = onHideControls,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private enum class PlayerKeyAction {
    /** Controls are hidden — this key reveals them (and is consumed, so it does nothing else). */
    REVEAL_CONTROLS,

    /** Controls are hidden — Left/Right reveal them focused straight on the seek bar. */
    REVEAL_CONTROLS_ON_SEEK_BAR,

    /** Back while controls are visible hides them instead of leaving the player. */
    HIDE_CONTROLS,

    /** Controls are already visible — let the key event proceed, but restart the auto-hide timer. */
    RESET_AUTO_HIDE_TIMER,

    IGNORE,
}

/** Which control grabs focus once [PlayerScreen] reveals the overlay. */
private enum class RevealFocusTarget {
    PLAY_PAUSE,
    SEEK_BAR,
}

private fun classifyPlayerKeyEvent(
    keyEvent: KeyEvent,
    controlsVisible: Boolean,
): PlayerKeyAction {
    if (keyEvent.type != KeyEventType.KeyDown) return PlayerKeyAction.IGNORE
    return when (keyEvent.key) {
        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter ->
            if (!controlsVisible) PlayerKeyAction.REVEAL_CONTROLS else PlayerKeyAction.RESET_AUTO_HIDE_TIMER
        Key.DirectionLeft, Key.DirectionRight ->
            if (!controlsVisible) PlayerKeyAction.REVEAL_CONTROLS_ON_SEEK_BAR else PlayerKeyAction.RESET_AUTO_HIDE_TIMER
        Key.Back ->
            if (controlsVisible) PlayerKeyAction.HIDE_CONTROLS else PlayerKeyAction.IGNORE
        else -> PlayerKeyAction.IGNORE
    }
}
