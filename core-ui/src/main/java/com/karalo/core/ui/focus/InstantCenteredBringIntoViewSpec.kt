package com.karalo.core.ui.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * [CenteredBringIntoViewSpec]'s exact same centering math, but with an instant, zero-duration
 * scroll instead of its animated tween.
 *
 * Used for a [com.karalo.core.ui.components.TvCarousel] row's own bring-into-view scroll while a
 * [com.karalo.core.ui.components.TvCarousel]'s explicit restore-by-key is landing focus on a
 * specific card (see that parameter's own doc): that restore already scrolls the target as close
 * to centered as it can compute up front, but Compose's own automatic "bring the newly-focused
 * descendant into view" correction still runs on top of it regardless, and in practice still finds
 * a small residual distance to close (a few percent of the card width) -- animating *that* still
 * reads as an unprompted scroll, since the person pressed BACK, not an arrow key, to get here.
 * Left as the default (animated) spec everywhere scrolling is a direct consequence of the person's
 * own arrow-key input.
 */
@OptIn(ExperimentalFoundationApi::class)
object InstantCenteredBringIntoViewSpec : BringIntoViewSpec {
    @Suppress("DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = snap()

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = CenteredBringIntoViewSpec.calculateScrollDistance(offset, size, containerSize)
}
