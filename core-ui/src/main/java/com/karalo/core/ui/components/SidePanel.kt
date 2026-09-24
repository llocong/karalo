package com.karalo.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

private const val SCRIM_ALPHA = 0.6f
private val DEFAULT_PANEL_WIDTH = 420.dp

/**
 * A panel that slides in from the right edge over a dimmed screen -- the TV pattern for a short
 * choice or a confirmation, without leaving the page. Place it last inside a full-screen [Box] so
 * it draws on top.
 *
 * While open it keeps D-pad focus inside itself (focus can't wander back onto the page behind
 * it), and BACK or LEFT call [onDismiss]. It doesn't pick a starting focus: the caller requests
 * focus on the right element once [visible] turns true (e.g. "Cancel" in a confirmation), since
 * only the caller knows which one is the safe default.
 */
@Composable
fun BoxScope.SidePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = DEFAULT_PANEL_WIDTH,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)))
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { fullWidth -> fullWidth },
        exit = slideOutHorizontally { fullWidth -> fullWidth },
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxHeight()
                    .width(width)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 40.dp, vertical = 48.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        val dismiss =
                            keyEvent.type == KeyEventType.KeyDown &&
                                (keyEvent.key == Key.Back || keyEvent.key == Key.DirectionLeft)
                        if (dismiss) onDismiss()
                        dismiss
                        // Trapped only while open: during the slide-out animation the content is still
                        // composed (and may still hold focus), and the caller must be able to move focus
                        // back onto the page -- e.g. to the button that opened the panel.
                    }.focusProperties { onExit = { if (visible) cancelFocusChange() } }
                    .focusGroup(),
            content = content,
        )
    }
}
