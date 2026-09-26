package com.karalo.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.karalo.core.common.model.SeasonalTheme

private fun karaloColorScheme(tokens: KaraloThemeTokens) =
    darkColorScheme(
        primary = KaraloVioletPrimary,
        primaryContainer = KaraloVioletDeep,
        secondary = tokens.accent,
        onSecondary = tokens.onAccent,
        tertiary = KaraloLilacTint,
        background = KaraloBackground,
        surface = tokens.surface,
        surfaceVariant = KaraloVioletDeep,
        onBackground = KaraloOnBackground,
        onSurface = KaraloOnBackground,
        onSurfaceVariant = KaraloOnSurfaceVariant,
        // The focus outline everywhere (cards, chips) -- the theme's accent, per the design.
        border = tokens.accent,
        error = KaraloError,
    )

@Composable
fun KaraloTheme(
    seasonalTheme: SeasonalTheme = SeasonalTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val tokens = remember(seasonalTheme) { karaloTokensFor(seasonalTheme) }
    val colorScheme = remember(tokens) { karaloColorScheme(tokens) }
    CompositionLocalProvider(LocalKaraloTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KaraloTypography,
            content = content,
        )
    }
}
