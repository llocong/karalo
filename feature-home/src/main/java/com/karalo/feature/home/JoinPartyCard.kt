package com.karalo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.KaraokeQrCode

// Bigger than the drawer's old 96dp QR for couch-distance scanning, but capped so the banner plus
// the whole Top Picks shelf (focused card's scale-up included) still fit on screen together --
// any taller and returning to Top Picks scrolls the banner's top edge under the safe zone.
private val QR_SIZE = 112.dp
private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val QR_SHAPE = RoundedCornerShape(8.dp)
private val CARD_PADDING = 16.dp
private val QR_TO_TEXT_SPACING = 28.dp

/**
 * "Scan to join the party" banner at the top of Home, reproducing the TV mock on the landing page
 * (backend/src/main/resources/web/index.html, "how it works" step 1): the join QR on the left,
 * a headline and a one-line hint on the right, on a translucent outlined card.
 *
 * Deliberately not focusable -- it's information, not an action, and a focus stop above Top Picks
 * would just be one more D-pad press between the person and the songs.
 */
@Composable
internal fun JoinPartyCard(
    joinUrl: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(CARD_SHAPE)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CARD_SHAPE)
                .padding(CARD_PADDING),
    ) {
        KaraokeQrCode(content = joinUrl, sizeDp = QR_SIZE, modifier = Modifier.clip(QR_SHAPE))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = QR_TO_TEXT_SPACING),
        ) {
            Text(
                text = "Scan to join the party",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Add songs from your phone",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
