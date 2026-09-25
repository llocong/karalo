package com.karalo.core.ui.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

private const val SCROLL_ANIMATION_DURATION_MS = 250

/**
 * Keeps the focused item's leading edge at [pivotPx] from the viewport's start -- a fixed "slot" a
 * few tiles in -- so the row only starts scrolling once focus passes that slot, then scrolls one
 * tile per move with focus staying put on screen. Like [CenteredBringIntoViewSpec], the scrollable
 * clamps the result at both ends of its content, so the first tiles stay pinned at the start and
 * the row stops flush with its last tile rather than scrolling past it.
 *
 * Used by the Playlists page's cover row (the "Karalo TV Playlists" design: 250ms ease-out).
 */
@OptIn(ExperimentalFoundationApi::class)
class SlotPivotBringIntoViewSpec(
    private val pivotPx: Float,
) : BringIntoViewSpec {
    // See CenteredBringIntoViewSpec for why this deprecated property is still what drives the scroll.
    @Suppress("DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = SCROLL_ANIMATION_DURATION_MS, easing = EaseOut)

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float =
        if (pivotPx + size <= containerSize) {
            offset - pivotPx
        } else {
            // The slot itself doesn't fit (a very narrow viewport): just bring the item fully into view.
            when {
                offset < 0f -> offset
                offset + size > containerSize -> offset + size - containerSize
                else -> 0f
            }
        }
}
