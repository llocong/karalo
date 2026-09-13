package com.karalo.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val KaraloColorScheme =
    darkColorScheme(
        primary = KaraloVioletPrimary,
        primaryContainer = KaraloVioletDeep,
        secondary = KaraloCoralAccent,
        onSecondary = KaraloBackground,
        tertiary = KaraloLilacTint,
        background = KaraloBackground,
        surface = KaraloSurface,
        surfaceVariant = KaraloVioletDeep,
        onBackground = KaraloOnBackground,
        onSurface = KaraloOnBackground,
        onSurfaceVariant = KaraloOnSurfaceVariant,
        border = KaraloOutline,
        error = KaraloError,
    )

@Composable
fun KaraloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KaraloColorScheme,
        typography = KaraloTypography,
        content = content,
    )
}
