package com.karalo.core.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.karalo.core.ui.R

// Fredoka (display/headlines — rounded and musical) and Manrope (body/UI — clean and legible at
// TV viewing distance), per the Karalo brand board. Both ship upstream only as variable fonts, so
// each named weight below is the same font file pinned to a "wght" axis instance.
@OptIn(ExperimentalTextApi::class)
private fun fredoka(weight: FontWeight) =
    Font(
        R.font.fredoka_variable,
        weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) =
    Font(
        R.font.manrope_variable,
        weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

private val FredokaMedium = FontFamily(fredoka(FontWeight.Medium))
private val FredokaSemiBold = FontFamily(fredoka(FontWeight.SemiBold))
private val ManropeRegular = FontFamily(manrope(FontWeight.Normal))
private val ManropeMedium = FontFamily(manrope(FontWeight.Medium))
private val ManropeSemiBold = FontFamily(manrope(FontWeight.SemiBold))
private val ManropeBold = FontFamily(manrope(FontWeight.Bold))
private val ManropeExtraBold = FontFamily(manrope(FontWeight.ExtraBold))

// The "Karalo" logo wordmark treatment — Fredoka is reserved for this and for the "Brand" font
// role below; every other practical bit of UI text (nav, card titles, body copy) uses the "Plain"
// role (Manrope) on the brand board.
val KaraloLogoTextStyle =
    TextStyle(fontFamily = FredokaSemiBold, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp)

// Video tile labels ("Title - Artist" on one text run, wrapping to 2 lines) -- Manrope Medium, a
// step lighter than body text's neighbors so a row of tiles reads as content rather than chrome.
val KaraloTileLabelTextStyle =
    TextStyle(
        fontFamily = ManropeMedium,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )

// A seasonal greeting under the wordmark on the waiting screen ("Happy Halloween") -- Fredoka
// Medium, a step lighter than the wordmark itself. No color: the caller uses the theme's accent.
val KaraloSplashGreetingTextStyle =
    TextStyle(
        fontFamily = FredokaMedium,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.02.em,
    )

// Nav drawer item labels, also used by the search field (typed text and placeholder) and the
// search suggestion chips so they all read identically -- Manrope SemiBold, like labelMedium, a
// size down from it. No color: each caller sets its own.
val KaraloNavLabelTextStyle =
    TextStyle(
        fontFamily = ManropeSemiBold,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )

// The full 15-style TV Material typography scale (developer.android.com/design/ui/tv/guides/
// styles/typography), sized up from the guide's phone-derived base tokens for 10-foot legibility.
// Font-role split follows the guide exactly: "Brand" (Fredoka) for Display/Headline/TitleLarge,
// "Plain" (Manrope) for TitleMedium/Small, Body*, and Label*. Weights deliberately deviate from the
// guide's Regular/Medium defaults in a few spots to match the brand board's voice — each exception
// is called out below; every deviation is a considered choice, not an oversight.
val KaraloTypography =
    Typography(
        // -- Brand (Fredoka SemiBold) --
        displayLarge =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.2).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        // Home's hero prompt.
        headlineMedium =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 30.sp,
            ),
        // -- Plain (Manrope) --
        // "Now Playing" title on the player overlay — Bold rather than the guide's Medium, to read
        // clearly over video content.
        titleMedium =
            TextStyle(
                fontFamily = ManropeBold,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.2.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = ManropeBold,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
            ),
        // Body copy and the nav/label style below carry no extra tracking (the guide's tokens are
        // 0.2-0.5sp): Manrope is already wide, and the extra spacing made text look looser than
        // the buttons next to it.
        bodyLarge =
            TextStyle(
                fontFamily = ManropeRegular,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = ManropeRegular,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = ManropeRegular,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        // Buttons (e.g. KaraloButton's CTA) — ExtraBold rather than the guide's Medium, to match
        // the brand board's punchy call-to-action treatment.
        labelLarge =
            TextStyle(
                fontFamily = ManropeExtraBold,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
        // Sidebar nav items — SemiBold for every item, active or not (only the color differs), per
        // the brand board's home-screen sidebar.
        labelMedium =
            TextStyle(
                fontFamily = ManropeSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
            ),
        // Compact badges (e.g. the result-card duration chip) — Bold rather than the guide's
        // Medium, to stay legible at this size from a couch.
        labelSmall =
            TextStyle(
                fontFamily = ManropeBold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.5.sp,
            ),
    )
