@file:Suppress("MagicNumber") // paths are the "Karalo Themes" design's own values

package com.karalo.core.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.karalo.core.ui.theme.HalloweenBat
import com.karalo.core.ui.theme.HalloweenPumpkin
import com.karalo.core.ui.theme.KaraloOnBackground

/**
 * The Halloween theme's decorations from the "Karalo Themes" design, shared by Home's backdrop and
 * the waiting screen. Purely decorative -- callers place them with `Image(..., contentDescription =
 * null)` behind their content.
 */
object HalloweenArt {
    /** A purple bat with a faint pumpkin outline, 2:1. */
    val Bat: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Bat",
                defaultWidth = 24.dp,
                defaultHeight = 12.dp,
                viewportWidth = 24f,
                viewportHeight = 12f,
            ).addPath(
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

    /**
     * A cobweb hanging from the top-right corner (mirror it for the top-left), in opaque white --
     * the design draws it faint, so give the `Image` an alpha.
     */
    val CornerWeb: ImageVector by lazy {
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
                    addPath(
                        pathData = addPathNodes(path),
                        stroke = SolidColor(KaraloOnBackground),
                        strokeLineWidth = 0.8f,
                    )
                }
            }.build()
    }
}
