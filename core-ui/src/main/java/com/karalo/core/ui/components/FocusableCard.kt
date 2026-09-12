package com.karalo.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.karalo.core.common.text.formatDuration

private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f
private const val DURATION_BADGE_BACKGROUND_ALPHA = 0.8f
private const val CARD_CORNER_RADIUS_DP = 8
private const val FOCUSED_SCALE = 1.1f
private const val UNFOCUSED_SCALE = 1f
private const val FOCUS_SCALE_DURATION_MS = 300
private const val UNFOCUS_SCALE_DURATION_MS = 500
private val FOCUSED_BORDER_WIDTH = 3.dp

/**
 * A D-pad-focusable result card: thumbnail image slot, title, and an optional subtitle, scaling up
 * with a border on focus so the currently-selected item is unambiguous from a couch. The video's
 * duration, when known, is badged over the bottom-right corner of the thumbnail, per the brand
 * board's home-screen video grid.
 *
 * Hand-rolled on plain [Modifier.clickable]/[Modifier.border]/[Modifier.graphicsLayer] rather than
 * `androidx.tv.material3`'s `Card`/`Surface` (the previous implementation): that component
 * unconditionally attaches a shadow-layer draw (`Modifier.tvSurfaceGlow`, on API 28+) and an
 * animated `zIndex` to *every* card regardless of focus state, even though this app never actually
 * shows a glow (every call site here leaves `glow` at its `Glow.None` default) -- on a real TV
 * device, with several of these visible at once in a shelf/results row, that invisible per-card
 * shadow draw was a measurable source of jank on every focus move (confirmed via `adb shell dumpsys
 * gfxinfo` framestat sampling: "Number Slow issue draw commands" tracked almost 1:1 with janky
 * frames). `Modifier.clickable` already handles D-pad center/Enter as a click on its own, so no
 * custom key handling is needed here either.
 */
@Composable
fun FocusableCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    durationSeconds: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(CARD_CORNER_RADIUS_DP.dp)
    // Matches the durations of tv-material's own default focus/unfocus scale tween (see
    // SurfaceScaleTokens) so this reads identically to the component it replaces.
    val scale by
        animateFloatAsState(
            targetValue = if (isFocused) FOCUSED_SCALE else UNFOCUSED_SCALE,
            animationSpec = tween(if (isFocused) FOCUS_SCALE_DURATION_MS else UNFOCUS_SCALE_DURATION_MS),
            label = "cardScale",
        )
    val containerColor =
        if (isFocused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    // Always attached (never conditionally added/removed across recomposition) with a transparent
    // color while unfocused, rather than only on isFocused -- functionally invisible either way,
    // but keeps the modifier chain's node count stable instead of inserting/removing a node on
    // every focus change.
    val borderColor = if (isFocused) MaterialTheme.colorScheme.border else Color.Transparent

    Column(
        modifier =
            modifier
                // Scale, corner clipping, and the container fill/border all read from the same
                // isFocused-derived values above -- combining scale with the shape/clip into one
                // graphicsLayer (rather than a separate Modifier.clip) keeps this to a single
                // RenderNode instead of two.
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.shape = shape
                    clip = true
                }
                // Keeps a focused card's scaled-up bounds drawing over its unfocused neighbors in
                // the same row instead of being overlapped by them (matches the zIndex bump
                // tv-material's Surface applies on focus) -- a plain jump rather than tv-material's
                // own animated zIndex float, since draw order doesn't need to ease in/out the way a
                // visible property like scale or color does.
                .zIndex(if (isFocused) 1f else 0f)
                .background(containerColor, shape)
                .border(BorderStroke(FOCUSED_BORDER_WIDTH, borderColor), shape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(bottom = 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(THUMBNAIL_ASPECT_RATIO),
            )
            if (durationSeconds != null && durationSeconds >= 0) {
                Text(
                    text = formatDuration(durationSeconds),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelSmall,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = DURATION_BADGE_BACKGROUND_ALPHA),
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
