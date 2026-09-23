package com.karalo.core.ui.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * A [BringIntoViewSpec] identical to the platform default (same "just bring it fully into view"
 * [calculateScrollDistance]) except for an instant, zero-duration scroll instead of the default
 * `spring()` animation.
 *
 * Used for Home's own shelf-to-shelf (vertical) bring-into-view scroll: returning from Player
 * restores focus straight onto whichever card was last played, which can be in a shelf well below
 * the top of the page -- an animated glide there reads as an unprompted scroll, since the person
 * pressed BACK, not an arrow key, to get here. Left as the default (animated) spec everywhere
 * scrolling is a direct consequence of the person's own arrow-key input -- see
 * [CenteredBringIntoViewSpec] for that case.
 */
@OptIn(ExperimentalFoundationApi::class)
object InstantBringIntoViewSpec : BringIntoViewSpec {
    @Suppress("DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = snap()
}
