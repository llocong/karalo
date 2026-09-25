package com.karalo.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karalo.core.ui.theme.KaraloSkeleton
import com.karalo.core.ui.theme.KaraloSkeletonHighlight

private const val SKELETON_TILE_COUNT = 4
private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f
private const val SHIMMER_DURATION_MS = 1400
private val SHIMMER_BAND_WIDTH = 240.dp

// Laid out like FocusableCard: a 16:9 thumbnail, then two 20dp label lines 9dp below it -- so
// the real row replaces this one without the page shifting.
private val THUMBNAIL_SHAPE = RoundedCornerShape(10.dp)
private val LABEL_TOP_SPACING = 9.dp
private val LABEL_LINE_HEIGHT = 20.dp
private val LABEL_BAR_HEIGHT = 12.dp
private val LABEL_BAR_SHAPE = RoundedCornerShape(LABEL_BAR_HEIGHT / 2)
private const val FIRST_LINE_WIDTH_FRACTION = 0.92f
private const val SECOND_LINE_WIDTH_FRACTION = 0.58f

/**
 * Stands in for a row of song tiles ([FocusableCard] in a [TvCarousel]) while it loads: four
 * placeholder tiles at [cardWidth], with the same [contentPadding] and [itemSpacing] as the real
 * row, running off the right edge like it. One shimmer sweeps left to right across the whole row.
 * Not focusable -- nothing to select until the songs arrive.
 */
@Composable
fun SkeletonShelf(
    cardWidth: Dp,
    contentPadding: PaddingValues,
    itemSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberShimmerProgress()
    Box(modifier = modifier.fillMaxWidth().clipToBounds().shimmer(shimmer)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            // Wider than the screen on purpose: the last tile is cut off at the edge, like the real row.
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).padding(contentPadding),
        ) {
            repeat(SKELETON_TILE_COUNT) { SkeletonTile(cardWidth) }
        }
    }
}

/** A single shimmering placeholder bar -- e.g. for a line of text that's still loading. */
@Composable
fun SkeletonBar(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberShimmerProgress()
    Box(
        modifier =
            modifier
                .size(width, height)
                .shimmer(shimmer)
                .background(KaraloSkeleton, RoundedCornerShape(height / 2)),
    )
}

@Composable
private fun SkeletonTile(cardWidth: Dp) {
    Column(modifier = Modifier.width(cardWidth)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(THUMBNAIL_ASPECT_RATIO)
                    .background(KaraloSkeleton, THUMBNAIL_SHAPE),
        )
        Spacer(modifier = Modifier.height(LABEL_TOP_SPACING))
        SkeletonLabelLine(FIRST_LINE_WIDTH_FRACTION)
        SkeletonLabelLine(SECOND_LINE_WIDTH_FRACTION)
    }
}

@Composable
private fun SkeletonLabelLine(widthFraction: Float) {
    Box(modifier = Modifier.height(LABEL_LINE_HEIGHT), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(widthFraction)
                    .height(LABEL_BAR_HEIGHT)
                    .background(KaraloSkeleton, LABEL_BAR_SHAPE),
        )
    }
}

@Composable
private fun rememberShimmerProgress(): State<Float> =
    rememberInfiniteTransition(label = "skeletonShimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(SHIMMER_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "skeletonShimmerProgress",
    )

/**
 * Sweeps a lighter band left to right over whatever this draws, tinting only the pixels already
 * drawn (SrcAtop on an offscreen layer) -- so the gaps between placeholders stay transparent. The
 * progress is only read at draw time, so the shimmer redraws without recomposing.
 */
private fun Modifier.shimmer(progress: State<Float>): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = SHIMMER_BAND_WIDTH.toPx()
            val bandStart = -band + (size.width + band) * progress.value
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, KaraloSkeletonHighlight, Color.Transparent),
                        startX = bandStart,
                        endX = bandStart + band,
                    ),
                blendMode = BlendMode.SrcAtop,
            )
        }
