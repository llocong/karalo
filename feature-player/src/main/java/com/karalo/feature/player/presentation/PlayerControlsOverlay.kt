package com.karalo.feature.player.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.theme.LocalKaraloTokens
import kotlin.math.roundToInt

private const val SEEK_STEP_MS = 10_000L
private val DOT_SIZE = 14.dp
private const val DOT_FOCUSED_SCALE = 1.5f

// Safe-zone content margins recommended by the TV layout guidelines
// (developer.android.com/design/ui/tv/guides/styles/layouts) -- the video itself stays full-bleed,
// but this overlay's own controls still need to clear the overscan margin.
private val SAFE_ZONE_HORIZONTAL = 58.dp
private val SAFE_ZONE_VERTICAL = 28.dp

@Composable
internal fun PlayerControlsOverlay(
    title: String,
    isPlaying: Boolean,
    hasNextInQueue: Boolean,
    positionMs: Long,
    durationMs: Long,
    playFocusRequester: FocusRequester,
    seekFocusRequester: FocusRequester,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onHideControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = SAFE_ZONE_HORIZONTAL, vertical = SAFE_ZONE_VERTICAL),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        SeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek,
            onHideControls = onHideControls,
            dotFocusRequester = seekFocusRequester,
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                onClick = onPlayPauseClick,
                enabled = true,
                modifier =
                    Modifier
                        .focusRequester(playFocusRequester)
                        // With Next disabled there's nothing to its right; without this, the
                        // focus search would jump up to the seekbar's dot instead.
                        .focusProperties { if (!hasNextInQueue) right = FocusRequester.Cancel },
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                )
            }
            // Disabled once the persistent remote queue is empty -- same rule the webapp's own
            // Skip button follows, not the local browse list's now-removed hasNext.
            // If Next has focus when the queue runs out, it stops being focusable (see
            // ControlButton) -- hand focus to Play/Pause rather than leaving the remote with
            // nothing focused.
            var nextHadFocus by remember { mutableStateOf(false) }
            LaunchedEffect(hasNextInQueue) {
                if (!hasNextInQueue && nextHadFocus) {
                    nextHadFocus = false
                    playFocusRequester.requestFocus()
                }
            }
            ControlButton(
                onClick = onNextClick,
                enabled = hasNextInQueue,
                modifier =
                    Modifier.onFocusChanged {
                        // Only tracked while enabled, so the focus loss caused by disabling
                        // doesn't erase the fact that Next was the focused control.
                        if (hasNextInQueue) nextHadFocus = it.isFocused
                    },
            ) {
                Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
    }
}

@Composable
private fun RowScope.ControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    // The default theme keeps TV Material's own neutral button colors; a seasonal theme tints the
    // icons and the focused fill with its accent.
    val tokens = LocalKaraloTokens.current
    val colors =
        if (tokens.isHalloween) {
            IconButtonDefaults.colors(
                contentColor = tokens.control,
                focusedContainerColor = tokens.control,
                focusedContentColor = tokens.onControl,
                pressedContainerColor = tokens.control,
                pressedContentColor = tokens.onControl,
            )
        } else {
            IconButtonDefaults.colors()
        }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        // TV Material keeps disabled buttons focusable; a disabled control here (Next with an
        // empty queue) is skipped by the D-pad instead, so focus never lands on a dead button.
        modifier = modifier.focusProperties { canFocus = enabled }.padding(end = 16.dp),
    ) {
        icon()
    }
}

/**
 * A track with a focusable "scrubber" dot at the current playback position: growing when focused,
 * and seeking [SEEK_STEP_MS] per Left/Right press while focused.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (positionMs: Long) -> Unit,
    onHideControls: () -> Unit,
    dotFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var isDotFocused by remember { mutableStateOf(false) }
    val dotScale by animateFloatAsState(if (isDotFocused) DOT_FOCUSED_SCALE else 1f, label = "dotScale")

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(DOT_SIZE)
                .onSizeChanged { trackWidthPx = it.width },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .align(Alignment.CenterStart)
                    .background(LocalKaraloTokens.current.control, RoundedCornerShape(3.dp)),
        )

        val dotSizePx = with(LocalDensity.current) { DOT_SIZE.roundToPx() }
        val dotOffsetX = (fraction * trackWidthPx).roundToInt() - dotSizePx / 2

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(x = dotOffsetX, y = 0) }
                    .size(DOT_SIZE)
                    .focusRequester(dotFocusRequester)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    }.onFocusChanged { isDotFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                onSeek((positionMs - SEEK_STEP_MS).coerceIn(0L, durationMs))
                                true
                            }
                            Key.DirectionRight -> {
                                onSeek((positionMs + SEEK_STEP_MS).coerceIn(0L, durationMs))
                                true
                            }
                            // The dot is the topmost focusable control, so there's nothing above
                            // it to navigate to — repurpose Up here to hide the controls instead.
                            Key.DirectionUp -> {
                                onHideControls()
                                true
                            }
                            else -> false
                        }
                    }.focusable()
                    .background(Color.White, CircleShape),
        )
    }
}
