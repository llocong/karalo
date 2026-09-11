package com.karalo.core.ui.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec

/**
 * A [BringIntoViewSpec] that pivots on the *center* of both the focused item and the viewport,
 * producing YouTube-on-Google-TV-style carousel scrolling: the focused item is kept centered while
 * scrolling through the middle of a row or column. This mirrors the shape of the platform's own
 * internal `PivotBringIntoViewSpec` (used by default on TV via [LocalBringIntoViewSpec], but
 * package-private so not reusable here) except with both the "parent" and "child" pivot fractions
 * at 0.5 instead of 0.3/0 -- i.e. the item's own center aligned to the viewport's center, not its
 * leading edge aligned 30% in from the start.
 *
 * Because a scrollable can never actually scroll past its real content bounds, the "desired"
 * offset this produces for the first couple of items is more negative than the list's true start
 * (clamped there, so they stay pinned at the list's own start padding), and for the last couple of
 * items exceeds the list's max scroll extent (clamped there instead) -- giving the
 * "pinned at start / centered through the middle / pinned at end" behavior with no extra per-item
 * state or hardcoded index thresholds. Works for either axis -- e.g. a horizontal shelf's `LazyRow`
 * or a vertical results grid's `LazyVerticalGrid` -- since [BringIntoViewSpec] is evaluated
 * per-axis by whichever scrollable is asking to bring its focused descendant into view.
 */
@OptIn(ExperimentalFoundationApi::class)
object CenteredBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val centeredTargetForLeadingEdge = (containerSize - size) / 2f
        // Defensive fallback mirroring the platform spec's own guard: only matters if a focused
        // item is ever bigger than the viewport itself (never true for our fixed-size cards on a
        // TV-sized screen), aligning the trailing edge instead of requesting an unsatisfiable
        // centered position.
        val spaceAvailable = containerSize - centeredTargetForLeadingEdge
        return if (size <= containerSize && spaceAvailable < size) {
            offset - (containerSize - size)
        } else {
            offset - centeredTargetForLeadingEdge
        }
    }
}
