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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.karalo.core.ui.theme.HalloweenBat
import com.karalo.core.ui.theme.HalloweenPumpkin

private val PUMPKIN_GLOW = HalloweenPumpkin.copy(alpha = 0.3f)
private val WEB_STROKE = Color(0x2EF5F3F7)

private val Bat: ImageVector by lazy {
    ImageVector
        .Builder(name = "Bat", defaultWidth = 24.dp, defaultHeight = 12.dp, viewportWidth = 24f, viewportHeight = 12f)
        .addPath(
            pathData =
                addPathNodes(
                    "M0 5 Q4 0 8 4 Q10 1 12 4 Q14 1 16 4 Q20 0 24 5 Q20 4 18 7 Q15 5 12 9 Q9 5 6 7 Q4 4 0 5Z",
                ),
            fill = SolidColor(HalloweenBat),
            stroke = SolidColor(HalloweenPumpkin),
            strokeAlpha = 0.55f,
            strokeLineWidth = 0.5f,
        ).build()
}

private val CornerWeb: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "CornerWeb",
            defaultWidth = 100.dp,
            defaultHeight = 100.dp,
            viewportWidth = 100f,
            viewportHeight = 100f,
        ).apply {
            for (path in listOf(
                "M100 0 L40 0 M100 0 L100 60 M100 0 L55 45 M100 0 L75 55 M100 0 L48 20",
                "M85 0 Q86 12 100 14 M70 0 Q72 22 100 28 M55 0 Q58 32 100 42",
            )) {
                addPath(pathData = addPathNodes(path), stroke = SolidColor(WEB_STROKE), strokeLineWidth = 0.8f)
            }
        }.build()
}

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
            imageVector = CornerWeb,
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).width(w * 0.2f),
        )
        Image(
            imageVector = Bat,
            contentDescription = null,
            modifier = Modifier.offset(x = w * 0.6f, y = h * 0.09f).width(w * 0.055f),
        )
        Image(
            imageVector = Bat,
            contentDescription = null,
            modifier =
                Modifier
                    .offset(x = w * 0.69f, y = h * 0.04f)
                    .width(w * 0.037f)
                    .rotate(-12f),
        )
    }
}
