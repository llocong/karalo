package com.karalo.feature.player.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.karalo.core.ui.components.ErrorState
import com.karalo.core.ui.components.LoadingIndicator
import kotlinx.coroutines.delay

private const val PROGRESS_POLL_INTERVAL_MS = 500L
private const val CONTROLS_AUTO_HIDE_MS = 4000L

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel) {
        while (true) {
            val player = viewModel.player
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(PROGRESS_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
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

        if (controlsVisible || !uiState.isPlaying) {
            PlayerControlsOverlay(
                title = uiState.currentItem?.title.orEmpty(),
                isPlaying = uiState.isPlaying,
                hasNext = uiState.hasNext,
                hasPrevious = uiState.hasPrevious,
                positionMs = positionMs,
                durationMs = durationMs,
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
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
