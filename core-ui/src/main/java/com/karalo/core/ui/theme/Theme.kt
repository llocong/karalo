package com.karalo.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val KaraloColorScheme =
    darkColorScheme(
        primary = KaraloPurple,
        primaryContainer = KaraloPurpleVariant,
        background = KaraloBackground,
        surface = KaraloSurface,
        surfaceVariant = KaraloSurfaceFocused,
        onBackground = KaraloOnBackground,
        onSurface = KaraloOnBackground,
        onSurfaceVariant = KaraloOnSurfaceVariant,
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
