package com.karalo.feature.player.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.karalo.core.ui.components.HalloweenArt
import com.karalo.core.ui.components.KaraloLogoMark
import com.karalo.core.ui.components.KaraloWordmark
import com.karalo.core.ui.components.KaraokeQrCode
import com.karalo.core.ui.theme.HalloweenBackground
import com.karalo.core.ui.theme.HalloweenDusk
import com.karalo.core.ui.theme.HalloweenGlow
import com.karalo.core.ui.theme.HalloweenPumpkin
import com.karalo.core.ui.theme.KaraloBackground
import com.karalo.core.ui.theme.KaraloOutline
import com.karalo.core.ui.theme.KaraloSplashGreetingTextStyle
import com.karalo.core.ui.theme.LocalKaraloTokens
import kotlin.math.sqrt

/** Same size/position as the player's own persistent overlay -- see [PlayerScreen]'s own doc for
 *  why the QR must stay in the identical bottom-left spot throughout all of Karaoke Mode. One
 *  single size for every state (playing, paused, waiting) -- deliberately not a bigger default
 *  with a playing-specific exception, so there's nothing to keep in sync/no risk of these drifting
 *  apart again. A third of the 220dp originally specced for it. */
internal val KARAOKE_QR_SIZE = 220.dp / KARAOKE_QR_SIZE_DIVISOR

private const val KARAOKE_QR_SIZE_DIVISOR = 3

private val SPLASH_LOGO_SIZE = 120.dp
private val SPLASH_SPACING = 24.dp

// Same size as the plain "Karalo" text it replaces (Fredoka 48sp).
private val SPLASH_WORDMARK_SIZE = 48.dp

/**
 * Shown in place of a frozen/blank video frame when the current song ends and the persistent
 * remote queue has nothing next -- still "in" Karaoke Mode (this composable lives inside
 * [PlayerScreen], not a separate NavHost destination), so the nav drawer stays inaccessible and
 * BACK still returns to the main app exactly as it does during normal playback. Per this feature's
 * spec: adding a song from a phone while this is showing should start playback automatically (see
 * [PlayerViewModel]'s `queueSnapshot` collector in its own `init`).
 *
 * Visually, this is the brand board's own "Splash screen" lockup (radial gradient + centered
 * logo/wordmark) rather than a plain status message -- an idle TV with nothing else on screen is
 * the one moment in Karaoke Mode where that lockup has room to breathe. The QR code keeps its
 * established bottom-left spot/size from the player's own overlay unchanged, since it's still the
 * only way to add a song from here.
 *
 * The Halloween theme gets that design's "Splash, Halloween" variant: a warm glow behind the
 * lockup, cobwebs and bats around it, and "Happy Halloween" under the wordmark.
 */
@Composable
fun KaraokeWaitingScreen(
    sessionJoinUrl: String?,
    modifier: Modifier = Modifier,
) {
    val isHalloween = LocalKaraloTokens.current.isHalloween
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (isHalloween) {
                        Modifier.drawBehind { drawHalloweenSplashBackground() }
                    } else {
                        Modifier.background(Brush.radialGradient(colors = listOf(KaraloOutline, KaraloBackground)))
                    },
                ),
    ) {
        if (isHalloween) HalloweenSplashDecorations()
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The mark's accent parts already follow the theme (pumpkin orange for Halloween).
            KaraloLogoMark(size = SPLASH_LOGO_SIZE)
            KaraloWordmark(fontSize = SPLASH_WORDMARK_SIZE, modifier = Modifier.padding(top = SPLASH_SPACING))
            if (isHalloween) {
                Text(
                    text = "Happy Halloween",
                    style = KaraloSplashGreetingTextStyle,
                    color = HalloweenPumpkin,
                    modifier = Modifier.padding(top = SPLASH_SPACING),
                )
            }
        }
        if (sessionJoinUrl != null) {
            KaraokeQrCode(
                content = sessionJoinUrl,
                sizeDp = KARAOKE_QR_SIZE,
                modifier = Modifier.align(Alignment.BottomStart).padding(24.dp),
            )
        }
    }
}

// The "Splash, Halloween" background: an ellipse centered 46% down, fading from the pumpkin-tinted
// glow through dusk to the Halloween ground.
private const val SPLASH_CENTER_Y = 0.46f
private const val DUSK_STOP = 0.38f
private const val GROUND_STOP = 0.72f

// A pumpkin glow bleeding in from past the top-right corner.
private const val CORNER_GLOW_CENTER_X = 0.88f
private const val CORNER_GLOW_TOP = -0.22f
private const val CORNER_GLOW_DIAMETER = 0.4f
private const val CORNER_GLOW_ALPHA = 0.28f
private const val CORNER_GLOW_CLEAR_STOP = 0.65f

private const val WEB_WIDTH = 0.16f
private const val WEB_ALPHA = 0.16f

/** One bat: its left/top as fractions of the screen's width/height, its width, and its tilt. */
private data class SplashBat(
    val left: Float,
    val top: Float,
    val width: Float,
    val rotation: Float,
)

private val SPLASH_BATS =
    listOf(
        SplashBat(left = 0.70f, top = 0.16f, width = 0.05f, rotation = 0f),
        SplashBat(left = 0.79f, top = 0.09f, width = 0.032f, rotation = -12f),
        SplashBat(left = 0.20f, top = 0.22f, width = 0.036f, rotation = 10f),
    )

/**
 * Draws the ellipse gradient the design specifies as CSS `radial-gradient(ellipse at 50% 46%, ...)`:
 * a circular gradient stretched horizontally to CSS's default farthest-corner ellipse (its radii
 * keep the closest-side aspect ratio and reach the farthest corner).
 */
private fun DrawScope.drawHalloweenSplashBackground() {
    val center = Offset(size.width / 2f, size.height * SPLASH_CENTER_Y)
    val closestX = size.width / 2f
    val closestY = size.height * SPLASH_CENTER_Y
    val farY = 1f - SPLASH_CENTER_Y
    val radiusY = size.height * sqrt(SPLASH_CENTER_Y * SPLASH_CENTER_Y + farY * farY)
    val radiusX = radiusY * closestX / closestY
    drawRect(HalloweenBackground)
    scale(scaleX = radiusX / radiusY, scaleY = 1f, pivot = center) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to HalloweenGlow,
                    DUSK_STOP to HalloweenDusk,
                    GROUND_STOP to HalloweenBackground,
                    center = center,
                    radius = radiusY,
                ),
            radius = radiusY,
            center = center,
        )
    }
    // Radial gradients default to farthest-corner too: for a circle centered in its own square box
    // that's the half-diagonal, so the glow fades out at 65% of it.
    val glowRadius = size.width * CORNER_GLOW_DIAMETER / 2f * sqrt(2f)
    val glowCenter =
        Offset(
            size.width * CORNER_GLOW_CENTER_X,
            size.height * CORNER_GLOW_TOP + size.width * CORNER_GLOW_DIAMETER / 2f,
        )
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to HalloweenPumpkin.copy(alpha = CORNER_GLOW_ALPHA),
                CORNER_GLOW_CLEAR_STOP to Color.Transparent,
                center = glowCenter,
                radius = glowRadius,
            ),
        radius = glowRadius,
        center = glowCenter,
    )
}

/** Cobwebs in both top corners and three bats -- purely decorative, behind the lockup. */
@Composable
private fun HalloweenSplashDecorations() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Image(
            imageVector = HalloweenArt.CornerWeb,
            contentDescription = null,
            alpha = WEB_ALPHA,
            modifier = Modifier.align(Alignment.TopEnd).width(w * WEB_WIDTH),
        )
        Image(
            imageVector = HalloweenArt.CornerWeb,
            contentDescription = null,
            alpha = WEB_ALPHA,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .width(w * WEB_WIDTH)
                    .graphicsLayer { scaleX = -1f },
        )
        for (bat in SPLASH_BATS) {
            Image(
                imageVector = HalloweenArt.Bat,
                contentDescription = null,
                modifier =
                    Modifier
                        .offset(x = w * bat.left, y = h * bat.top)
                        .width(w * bat.width)
                        .rotate(bat.rotation),
            )
        }
    }
}
