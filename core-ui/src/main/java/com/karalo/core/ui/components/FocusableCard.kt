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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.karalo.core.common.text.formatDuration
import com.karalo.core.ui.theme.KaraloBadgeBackground
import com.karalo.core.ui.theme.KaraloTileLabelTextStyle

private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f
private const val CARD_CORNER_RADIUS_DP = 10
private const val FOCUSED_SCALE = 1.1f
private const val UNFOCUSED_SCALE = 1f
private const val FOCUS_SCALE_DURATION_MS = 300
private const val UNFOCUS_SCALE_DURATION_MS = 500
private val FOCUSED_BORDER_WIDTH = 3.dp
private val LABEL_TOP_SPACING = 9.dp
private val BADGE_INSET = 7.dp
private val BADGE_SHAPE = RoundedCornerShape(5.dp)

/**
 * A D-pad-focusable video tile: a rounded 16:9 thumbnail with its label (title, then an optional
 * subtitle) underneath on the page background, scaling up with an accent outline around the
 * thumbnail on focus so the currently-selected item is unambiguous from a couch. The video's
 * duration, when known, is badged over the bottom-right corner of the thumbnail, per the "Karalo
 * Themes" design's home-screen rows.
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
    // SurfaceScaleTokens) so this reads identically to the component it replaces. Runs concurrently
    // with the row's own centering scroll (started independently, by Compose's own focus-triggered
    // bring-into-view) rather than waiting for it to settle first -- an earlier version of this
    // component deliberately sequenced the two (wait for scroll to finish, then animate scale) on
    // the hypothesis that running both at once was what caused a real stutter, but that stutter's
    // actual root cause was this scale animation changing bounds the scroll was itself reading every
    // frame (see the graphicsLayer comment below) -- once that structural coupling was fixed, the
    // sequencing was no longer needed to fix stutter and only added a perceptible delay before the
    // focus "pop" started, confirmed by user feedback on a real TV. Removed rather than shortened.
    val scale by
        animateFloatAsState(
            targetValue = if (isFocused) FOCUSED_SCALE else UNFOCUSED_SCALE,
            animationSpec = tween(if (isFocused) FOCUS_SCALE_DURATION_MS else UNFOCUS_SCALE_DURATION_MS),
            label = "cardScale",
        )
    // Always attached (never conditionally added/removed across recomposition) with a transparent
    // color while unfocused, rather than only on isFocused -- functionally invisible either way,
    // but keeps the modifier chain's node count stable instead of inserting/removing a node on
    // every focus change.
    val borderColor = if (isFocused) MaterialTheme.colorScheme.border else Color.Transparent

    Box(
        modifier =
            modifier
                // Keeps a focused card's scaled-up bounds drawing over its unfocused neighbors in
                // the same row instead of being overlapped by them (matches the zIndex bump
                // tv-material's Surface applies on focus) -- a plain jump rather than tv-material's
                // own animated zIndex float, since draw order doesn't need to ease in/out the way a
                // visible property like scale or color does. This has to live on the outer,
                // *unscaled* node (see graphicsLayer comment below) since zIndex only affects
                // ordering among this node's own siblings in the row.
                .zIndex(if (isFocused) 1f else 0f)
                // Deliberately the focus/click target, not the scaled Column below: the scroll-into-
                // view animation that centers a newly-focused card re-reads this node's live bounds
                // every frame for the whole scroll. If the scale animation below were applied to this
                // same node (as it originally was), those bounds would keep growing for the entire
                // 300ms focus-in tween, and the scroll would have to keep chasing a moving target --
                // visible as a fast scroll followed by a small correcting snap right as the scale
                // animation finishes. Keeping this node's bounds fixed for the whole animation (the
                // scale lives one level in, on a plain visual descendant) gives the scroll a stable
                // target from the first frame, eliminating that trailing correction.
                //
                // canFocus is forced on because clickable's own focusability is "system defined"
                // (Compose 1.8+): not focusable while the device is in touch mode, mirroring a
                // View's focusableInTouchMode=false. A device that boots in touch mode (confirmed
                // on the phone-image TV AVD, or after any tap/click on its screen) then rejects
                // every requestFocus() onto these cards, leaving Home's initial focus stranded on
                // its placeholder with no card reachable by D-pad.
                .focusProperties { canFocus = true }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
                    // Scales the thumbnail and its label together, so the label stays attached
                    // under the tile as it grows.
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
        ) {
            // Only the thumbnail is a rounded "card" -- the label sits on the page background below
            // it, outside the focus outline. The corner clip and outline share one graphicsLayer.
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .graphicsLayer {
                            this.shape = shape
                            clip = true
                        }
                        // Placeholder fill while the thumbnail loads (or if it has none).
                        .background(MaterialTheme.colorScheme.surface)
                        .border(BorderStroke(FOCUSED_BORDER_WIDTH, borderColor), shape),
            ) {
                val context = LocalContext.current
                AsyncImage(
                    // Precision.INEXACT matches TvCarousel's adjacent-item prefetch requests (see
                    // TvCarousel.kt/TvCarousel.md): a prefetch request has no real layout pass to
                    // size against, so it can only guess a target size -- INEXACT is what lets that
                    // guess still produce a cache hit here, at this request's own real,
                    // layout-resolved size.
                    model =
                        remember(thumbnailUrl) {
                            ImageRequest
                                .Builder(context)
                                .data(thumbnailUrl)
                                .precision(Precision.INEXACT)
                                .build()
                        },
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
                                .padding(BADGE_INSET)
                                .background(KaraloBadgeBackground, BADGE_SHAPE)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = title,
                // Explicit, unlike the library default: androidx.tv.material3.Text falls back to
                // LocalContentColor when neither this param nor the style itself sets one, and
                // LocalContentColor's own library default is plain black -- normally invisible
                // because androidx.tv.material3's Surface/Card provide a real one derived from the
                // container color, but this component deliberately doesn't use Surface/Card (see
                // this file's own doc for why), so nothing here overrides that black default
                // without setting it explicitly.
                color = MaterialTheme.colorScheme.onBackground,
                style = KaraloTileLabelTextStyle,
                // Up to 2 lines then an ellipsis; always reserving both keeps every tile in a row
                // the same height, so the row doesn't shift as it scrolls past short titles.
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = LABEL_TOP_SPACING),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = KaraloTileLabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
