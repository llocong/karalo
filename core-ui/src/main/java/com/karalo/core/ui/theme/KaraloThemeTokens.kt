@file:Suppress("MagicNumber") // gradient stops and angles are the design's own values

package com.karalo.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import com.karalo.core.common.model.SeasonalTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Brand values that follow the active [SeasonalTheme] but have no slot in TV Material's color
 * scheme (brushes, the QR panel's translucent fill, the stronger outline). Read through
 * [LocalKaraloTokens]; [KaraloTheme] provides it.
 */
@Immutable
data class KaraloThemeTokens(
    val seasonalTheme: SeasonalTheme,
    val accent: Color,
    val onAccent: Color,
    val surface: Color,
    val outline: Color,
    val outlineStrong: Color,
    val pageBackground: Brush,
    val sidebarBackground: Brush,
    val qrPanel: Color,
    // Filled brand controls (the search mic, the player's seekbar progress and focused buttons):
    // violet by default, taken over by the accent in seasonal themes. [onControl] is their content.
    val control: Color,
    val onControl: Color,
) {
    val isHalloween: Boolean get() = seasonalTheme == SeasonalTheme.HALLOWEEN
}

/** 12% white -- the nav rail's focused/active item fill, the same in every theme. */
val KaraloRailItemActive = Color(0x1FF5F3F7)

/** The duration badge's translucent near-black, the same in every theme. */
val KaraloBadgeBackground = Color(0xCC0B0710)

internal fun karaloTokensFor(theme: SeasonalTheme): KaraloThemeTokens =
    when (theme) {
        SeasonalTheme.DEFAULT ->
            KaraloThemeTokens(
                seasonalTheme = theme,
                accent = KaraloCoralAccent,
                onAccent = KaraloBackground,
                surface = KaraloSurface,
                outline = KaraloOutline,
                outlineStrong = KaraloOutlineStrong,
                pageBackground = SolidColor(KaraloBackground),
                sidebarBackground = CssLinearGradient(135f, KaraloVioletDeep, KaraloSurface),
                qrPanel = KaraloSurface.copy(alpha = 0.8f),
                control = KaraloVioletPrimary,
                onControl = Color.White,
            )
        SeasonalTheme.HALLOWEEN ->
            KaraloThemeTokens(
                seasonalTheme = theme,
                accent = HalloweenPumpkin,
                onAccent = HalloweenOnPumpkin,
                surface = HalloweenSurface,
                outline = HalloweenOutline,
                outlineStrong = HalloweenOutlineStrong,
                pageBackground = TopRightGlow(HalloweenGlow, HalloweenBackground),
                sidebarBackground = CssLinearGradient(160f, HalloweenSidebarStart, HalloweenSidebarEnd),
                qrPanel = HalloweenBackground.copy(alpha = 0.6f),
                control = HalloweenPumpkin,
                onControl = HalloweenOnPumpkin,
            )
    }

val LocalKaraloTokens = staticCompositionLocalOf { karaloTokensFor(SeasonalTheme.DEFAULT) }

/**
 * CSS `linear-gradient(<angle>deg, start, end)` semantics -- the angle is measured clockwise from
 * "to top", and the gradient line is long enough that both corners land exactly on the end stops --
 * so the design's gradients read the same at any aspect ratio (Compose's own linearGradient takes
 * absolute points instead).
 */
private class CssLinearGradient(
    private val angleDegrees: Float,
    private val start: Color,
    private val end: Color,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = Math.toRadians(angleDegrees.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val halfLength = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(
            from = center - Offset(dx, dy) * halfLength,
            to = center + Offset(dx, dy) * halfLength,
            colors = listOf(start, end),
        )
    }

    override fun equals(other: Any?) =
        other is CssLinearGradient && other.angleDegrees == angleDegrees && other.start == start && other.end == end

    override fun hashCode() = (angleDegrees.hashCode() * 31 + start.hashCode()) * 31 + end.hashCode()
}

/**
 * `radial-gradient(at 85% 0%, glow 0%, base 55%)` -- a warm glow bleeding in from the top-right
 * corner, fading to [base] a little past the middle of the screen.
 */
private class TopRightGlow(
    private val glow: Color,
    private val base: Color,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val center = Offset(size.width * 0.85f, 0f)
        val farthestCorner = hypot(center.x, size.height)
        return RadialGradientShader(
            center = center,
            radius = farthestCorner.coerceAtLeast(1f),
            colors = listOf(glow, base, base),
            colorStops = listOf(0f, 0.55f, 1f),
        )
    }

    override fun equals(other: Any?) = other is TopRightGlow && other.glow == glow && other.base == base

    override fun hashCode() = glow.hashCode() * 31 + base.hashCode()
}
