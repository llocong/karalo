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

    val Playlists: ImageVector by lazy {
        lineIcon("Playlists", "M3 6h11M3 11h11M3 16h7", "M17 17.5V5l4 1.5", circle(15.5f, 17.5f, 1.8f))
    }

    val History: ImageVector by lazy {
        lineIcon("History", circle(12f, 12f, 9f), "M12 7v5l3 2")
    }

    /** The update card's download arrow. */
    val Download: ImageVector by lazy {
        lineIcon("Download", "M12 4v11", "M7 11l5 5 5-5", "M5 20h14", strokeWidth = 2.4f)
    }

    /** The update card's "ready to restart" arrow. */
    val Restart: ImageVector by lazy {
        lineIcon("Restart", "M20 11a8 8 0 1 0-2.3 5.7", "M20 5v6h-6", strokeWidth = 2.4f)
    }

    /** "‹ Settings" on the What's new page. */
    val ChevronLeft: ImageVector by lazy {
        lineIcon("ChevronLeft", "M15 5l-7 7 7 7", strokeWidth = 2.4f)
    }

    /** The What's new page's "More below" chip. */
    val ChevronDown: ImageVector by lazy {
        lineIcon("ChevronDown", "M6 9l6 6 6-6", strokeWidth = 2.6f)
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
    strokeWidth: Float = STROKE_WIDTH,
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
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
