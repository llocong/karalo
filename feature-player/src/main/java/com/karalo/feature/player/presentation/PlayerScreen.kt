package com.karalo.feature.player.presentation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.ErrorState
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
    val rootFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }

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
        if (controlsVisible) playFocusRequester.requestFocus()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val action = classifyPlayerKeyEvent(keyEvent, controlsVisible)
                    if (keyEvent.key == androidx.compose.ui.input.key.Key.Back) {
                        android.util.Log.d(
                            "KaraloNavDebug",
                            "PlayerScreen Back key type=${keyEvent.type} " +
                                "controlsVisible=$controlsVisible action=$action",
                        )
                    }
                    when (action) {
                        PlayerKeyAction.REVEAL_CONTROLS -> {
                            interactionTick++
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

        when {
            uiState.error != null ->
                ErrorState(
                    message = uiState.error.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )
            uiState.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
        }

        if (controlsVisible) {
            PlayerControlsOverlay(
                title = formatVideoTitle(uiState.currentItem?.title.orEmpty()),
                isPlaying = uiState.isPlaying,
                hasNext = uiState.hasNext,
                hasPrevious = uiState.hasPrevious,
                positionMs = positionMs,
                durationMs = durationMs,
                playFocusRequester = playFocusRequester,
                onPlayPauseClick = {
                    controlsVisible = true
                    viewModel.togglePlayPause()
                },
                onNextClick = {
                    controlsVisible = true
                    viewModel.next()
                },
                onPreviousClick = {
                    controlsVisible = true
                    viewModel.previous()
                },
                onSeek = viewModel::seekTo,
                onHideControls = { controlsVisible = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private enum class PlayerKeyAction {
    /** Controls are hidden — this key reveals them (and is consumed, so it does nothing else). */
    REVEAL_CONTROLS,

    /** Back while controls are visible hides them instead of leaving the player. */
    HIDE_CONTROLS,

    /** Controls are already visible — let the key event proceed, but restart the auto-hide timer. */
    RESET_AUTO_HIDE_TIMER,

    IGNORE,
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
            if (controlsVisible) PlayerKeyAction.RESET_AUTO_HIDE_TIMER else PlayerKeyAction.IGNORE
        Key.Back ->
            if (controlsVisible) PlayerKeyAction.HIDE_CONTROLS else PlayerKeyAction.IGNORE
        else -> PlayerKeyAction.IGNORE
    }
}
