@file:Suppress("MagicNumber") // every literal here IS the named constant -- that's the point of a palette file

package com.karalo.feature.search.presentation

import androidx.compose.ui.graphics.Color

// The search bar / suggestion-chip area is a deliberate, confirmed departure from the rest of the
// app's violet-tinted MaterialTheme.colorScheme (see core-ui's Color.kt/Theme.kt) -- flat
// black/white/gray only, no accent color (the mic button's purple fill is the one exception, and
// intentionally keeps using MaterialTheme.colorScheme.primary instead of a value here). Scoped
// locally to this feature since no other screen wants a neutral-gray token.
internal val SearchPillFill = Color(0xFF2C2C2E)
internal val SearchTypedText = Color(0xFFD8D8DC)
internal val SearchPlaceholderText = Color(0xFF8A8A8E)
internal val SearchChipFocusedBackground = Color(0xFFFFFFFF)
internal val SearchChipFocusedText = Color(0xFF1A1A1A)
internal val SearchChipUnfocusedText = Color(0xFFFFFFFF)
