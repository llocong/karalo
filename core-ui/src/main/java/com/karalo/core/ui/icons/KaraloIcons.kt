@file:Suppress("MagicNumber") // icon coordinates

package com.karalo.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 2.2f

/**
 * The nav rail's line icons from the "Karalo Themes" design: 24-unit viewport, 2.2 stroke, round
 * caps and joins. Stroked in opaque black so [androidx.tv.material3.Icon]'s tint recolors them
 * like any Material icon.
 */
object KaraloIcons {
    val Search: ImageVector by lazy {
        lineIcon("Search", circle(11f, 11f, 7f), "M16.5 16.5L21 21")
    }

    val Home: ImageVector by lazy {
        lineIcon("Home", "M3 11l9-7 9 7", "M5 10v10h14V10")
    }

    val History: ImageVector by lazy {
        lineIcon("History", circle(12f, 12f, 9f), "M12 7v5l3 2")
    }

    val Settings: ImageVector by lazy {
        lineIcon(
            "Settings",
            "M10.3 3.2h3.4l.5 2.4 1.6.9 2.3-.8 1.7 2.9-1.8 1.6v1.8l1.8 1.6-1.7 2.9-2.3-.8-1.6.9-.5 2.4h-3.4" +
                "l-.5-2.4-1.6-.9-2.3.8-1.7-2.9 1.8-1.6v-1.8L4.2 8.6l1.7-2.9 2.3.8 1.6-.9z",
            circle(12f, 12f, 2.8f),
        )
    }
}

private fun circle(
    cx: Float,
    cy: Float,
    r: Float,
) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun lineIcon(
    name: String,
    vararg paths: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = VIEWPORT.dp,
            defaultHeight = VIEWPORT.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            for (path in paths) {
                addPath(
                    pathData = addPathNodes(path),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE_WIDTH,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
