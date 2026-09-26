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
val KaraloOutlineStrong = Color(0xFF3A2D47)
val KaraloTextSecondary = Color(0xFFC9BFD6)

// Loading placeholders (see SkeletonShelf) and the lighter band their shimmer sweeps across them.
val KaraloSkeleton = Color(0xFF1E1528)
val KaraloSkeletonHighlight = Color(0xFF2E2240)

// "Halloween" seasonal theme (see the "Karalo Themes" Claude Design file) -- pumpkin orange takes
// over the coral accent's role, over a warmer, redder-black ground.
val HalloweenPumpkin = Color(0xFFFF7A1A)
val HalloweenOnPumpkin = Color(0xFF1A0B02)
val HalloweenBackground = Color(0xFF0A0610)
val HalloweenGlow = Color(0xFF3A1A10)
val HalloweenSurface = Color(0xFF170E1B)
val HalloweenOutline = Color(0xFF2E1B2E)
val HalloweenOutlineStrong = Color(0xFF46283F)
val HalloweenRaised = Color(0xFF26162A)
val HalloweenSidebarStart = Color(0xFF3B1466)
val HalloweenSidebarEnd = Color(0xFF120812)
val HalloweenBat = Color(0xFF4A2A5E)

// The middle stop of the Halloween waiting screen's background, between the glow and the ground.
val HalloweenDusk = Color(0xFF1E0E24)

// Not part of the brand board (which doesn't specify an error color) — chosen to read clearly as
// "error" against the violet/coral palette rather than being mistaken for the coral accent.
val KaraloError = Color(0xFFCF6679)
