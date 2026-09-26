package com.karalo.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.karalo.core.ui.theme.KaraloOnBackground
import com.karalo.core.ui.theme.LocalKaraloTokens

// The "Karalo Themes" design's TV buttons: 1cqw x 2.4cqw padding, 1.5cqw text (1cqw = 9.6dp).
private val PILL_PADDING_HORIZONTAL = 23.dp
private val PILL_PADDING_VERTICAL = 9.6.dp
private val PILL_FONT_SIZE = 14.5.sp

// Focus is a 0.3cqw white outline, 0.3cqw outside the button (CSS outline + outline-offset).
private val FOCUS_OUTLINE_WIDTH = 3.dp
private val FOCUS_OUTLINE_OFFSET = 3.dp

/**
 * A pill-shaped TV button: [primary] is filled with the theme accent, otherwise it sits on the
 * theme's raised color. Focus draws an outline around it instead of scaling it, so neighbouring
 * buttons never shift.
 */
@Composable
fun KaraloPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val tokens = LocalKaraloTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val container = if (primary) tokens.accent else tokens.raised
    val content = if (primary) tokens.onAccent else KaraloOnBackground
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            modifier.drawBehind {
                if (!focused) return@drawBehind
                val stroke = FOCUS_OUTLINE_WIDTH.toPx()
                // The stroke is centered on the path, so the path runs through the outline's middle.
                val inset = FOCUS_OUTLINE_OFFSET.toPx() + stroke / 2
                val outlineSize = Size(size.width + inset * 2, size.height + inset * 2)
                drawRoundRect(
                    color = KaraloOnBackground,
                    topLeft = Offset(-inset, -inset),
                    size = outlineSize,
                    cornerRadius = CornerRadius(outlineSize.height / 2),
                    style = Stroke(width = stroke),
                )
            },
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = content,
                focusedContainerColor = container,
                focusedContentColor = content,
                pressedContainerColor = container,
                pressedContentColor = content,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = PILL_FONT_SIZE,
                    fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold,
                ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = PILL_PADDING_HORIZONTAL, vertical = PILL_PADDING_VERTICAL),
        )
    }
}
