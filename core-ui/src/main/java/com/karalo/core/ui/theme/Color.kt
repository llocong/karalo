@file:Suppress("MagicNumber") // every literal here IS the named constant — that's the point of a palette file

package com.karalo.core.ui.theme

import androidx.compose.ui.graphics.Color

// Karalo brand palette (see the "Karalo Karaoke Branding" Claude Design project) — deep violet
// grounds the app for dark-room TV viewing, coral carries energy for actions and highlights.
val KaraloVioletPrimary = Color(0xFF7C3AED)
val KaraloVioletDeep = Color(0xFF4C1D95)
val KaraloLilacTint = Color(0xFFC4B5FD)
val KaraloCoralAccent = Color(0xFFFF5D8F)
val KaraloSurface = Color(0xFF17101F)
val KaraloBackground = Color(0xFF0B0710)
val KaraloOnBackground = Color(0xFFF5F3F7)
val KaraloOnSurfaceVariant = Color(0xFFA79BB5)
val KaraloOutline = Color(0xFF241834)

// Not part of the brand board (which doesn't specify an error color) — chosen to read clearly as
// "error" against the violet/coral palette rather than being mistaken for the coral accent.
val KaraloError = Color(0xFFCF6679)
