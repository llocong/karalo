package com.karalo.feature.player.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.KaraokeQrCode
import com.karalo.core.ui.theme.KaraloBackground
import com.karalo.core.ui.theme.KaraloLogoTextStyle
import com.karalo.core.ui.theme.KaraloOutline
import com.karalo.core.ui.R as CoreUiR

/** Same size/position as the player's own persistent overlay -- see [PlayerScreen]'s own doc for
 *  why the QR must stay in the identical bottom-left spot throughout all of Karaoke Mode. One
 *  single size for every state (playing, paused, waiting) -- deliberately not a bigger default
 *  with a playing-specific exception, so there's nothing to keep in sync/no risk of these drifting
 *  apart again. A third of the 220dp originally specced for it. */
internal val KARAOKE_QR_SIZE = 220.dp / KARAOKE_QR_SIZE_DIVISOR

private const val KARAOKE_QR_SIZE_DIVISOR = 3

private val SPLASH_LOGO_SIZE = 120.dp

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
 */
@Composable
fun KaraokeWaitingScreen(
    sessionJoinUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.radialGradient(colors = listOf(KaraloOutline, KaraloBackground))),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(CoreUiR.drawable.ic_karalo_logo),
                contentDescription = null,
                modifier = Modifier.size(SPLASH_LOGO_SIZE),
            )
            Text(
                text = "Karalo",
                style = KaraloLogoTextStyle.copy(fontSize = 48.sp, lineHeight = 56.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 24.dp),
            )
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
