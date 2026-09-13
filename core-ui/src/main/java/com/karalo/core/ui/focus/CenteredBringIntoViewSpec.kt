package com.karalo.core.ui.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec

private const val SCROLL_ANIMATION_DURATION_MS = 120

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
    // scrollAnimationSpec is annotated @Deprecated ("customization is no longer supported") but is
    // still what actually drives the scroll: `ContentInViewNode.launchAnimation()` (Compose
    // Foundation, confirmed by reading its source) builds its animation from
    // `requireBringIntoViewSpec().scrollAnimationSpec` verbatim. Its default is a fast, no-bounce
    // `spring()` -- on a real TV remote this reads as an instant jump rather than visible motion,
    // which itself presents as stutter. An eased tween makes the centering glide legible.
    //
    // Compose's own arrow-key focus search moves focus (a logical, instant state change) the
    // moment each key event arrives; this scroll animation is what visually catches the viewport
    // up to wherever focus already is, and a `tween` always takes its full configured duration to
    // converge regardless of distance, retargeting from scratch on every new bring-into-view
    // request. Confirmed on a real remote: at the original 250ms, pressing LEFT/RIGHT
    // several times in quick succession (faster than 250ms apart, but each still a single,
    // non-repeat press -- not held) let focus outrun the animation, leaving the focused card
    // scrolled off-screen for a moment. Shortened so a single retarget resolves well within a
    // realistic fast-press interval, keeping the focused card visible throughout.
    @Suppress("DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = SCROLL_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)

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
