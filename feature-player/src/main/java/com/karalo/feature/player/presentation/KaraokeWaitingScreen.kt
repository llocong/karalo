package com.karalo.feature.player.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.KaraokeQrCode

/** Same size/position as the player's own persistent overlay -- see [PlayerScreen]'s own doc for
 *  why the QR must stay in the identical bottom-left spot throughout all of Karaoke Mode. */
internal val KARAOKE_QR_SIZE = 220.dp

/**
 * Shown in place of a frozen/blank video frame when the current song ends and the persistent
 * remote queue has nothing next -- still "in" Karaoke Mode (this composable lives inside
 * [PlayerScreen], not a separate NavHost destination), so the nav drawer stays inaccessible and
 * BACK still returns to the main app exactly as it does during normal playback. Per this feature's
 * spec: adding a song from a phone while this is showing should start playback automatically (see
 * [PlayerViewModel]'s `queueSnapshot` collector in its own `init`).
 */
@Composable
fun KaraokeWaitingScreen(
    sessionJoinUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Waiting for the next song…",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Scan the QR code to add a song from your phone",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
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
