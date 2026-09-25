@file:Suppress("MagicNumber") // positions and paths are the "Karalo Themes" design's own values

package com.karalo.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.karalo.core.ui.components.HalloweenArt
import com.karalo.core.ui.theme.HalloweenPumpkin

private val PUMPKIN_GLOW = HalloweenPumpkin.copy(alpha = 0.3f)

// A touch stronger than the waiting screen's cobwebs.
private const val WEB_ALPHA = 0.18f

/**
 * The Halloween theme's Home backdrop: a pumpkin glow and a cobweb in the top-right corner, and two
 * bats. Purely decorative -- drawn behind the content, never focusable. Sized from the screen area
 * it fills so it keeps the design's proportions at any resolution.
 */
@Composable
internal fun HalloweenDecorations(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.width * 0.21f
            val center = Offset(size.width - radius * 0.6f, radius * 0.1f)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to PUMPKIN_GLOW,
                        0.65f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                radius = radius,
                center = center,
            )
        }
        Image(
            imageVector = HalloweenArt.CornerWeb,
            contentDescription = null,
            alpha = WEB_ALPHA,
            modifier = Modifier.align(Alignment.TopEnd).width(w * 0.2f),
        )
        Image(
            imageVector = HalloweenArt.Bat,
            contentDescription = null,
            modifier = Modifier.offset(x = w * 0.6f, y = h * 0.09f).width(w * 0.055f),
        )
        Image(
            imageVector = HalloweenArt.Bat,
            contentDescription = null,
            modifier =
                Modifier
                    .offset(x = w * 0.69f, y = h * 0.04f)
                    .width(w * 0.037f)
                    .rotate(-12f),
        )
    }
}
