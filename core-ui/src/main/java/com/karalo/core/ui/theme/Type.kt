package com.karalo.core.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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
private val ManropeExtraBold = FontFamily(manrope(FontWeight.ExtraBold))

// Larger than phone-scale Material defaults — legible from a couch at 10-foot viewing distance.
val KaraloTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FredokaSemiBold,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FredokaMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FredokaMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = ManropeRegular,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = ManropeRegular,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = ManropeExtraBold,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
    )
