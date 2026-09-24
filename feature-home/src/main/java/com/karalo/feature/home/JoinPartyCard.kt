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
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.KaraokeQrCode
import com.karalo.core.ui.theme.KaraloTextSecondary
import com.karalo.core.ui.theme.LocalKaraloTokens

// Per the "Karalo Themes" design: a 7.5cqw code plus its light quiet-zone pad, sized so the banner
// plus the whole first shelf (focused tile's scale-up included) fit on screen together.
private val QR_SIZE = 84.dp
private val CARD_SHAPE = RoundedCornerShape(13.dp)
private val QR_SHAPE = RoundedCornerShape(6.dp)
private val CARD_PADDING = 15.dp
private val QR_TO_TEXT_SPACING = 19.dp
private val TITLE_FONT_SIZE = 21.sp
private val HINT_FONT_SIZE = 14.sp

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
    val tokens = LocalKaraloTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(CARD_SHAPE)
                .background(tokens.qrPanel)
                .border(1.dp, tokens.outlineStrong, CARD_SHAPE)
                .padding(CARD_PADDING),
    ) {
        KaraokeQrCode(content = joinUrl, sizeDp = QR_SIZE, modifier = Modifier.clip(QR_SHAPE))
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(start = QR_TO_TEXT_SPACING),
        ) {
            Text(
                text = "Scan to join the party",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = TITLE_FONT_SIZE, lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Add songs from your phone",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = HINT_FONT_SIZE),
                color = KaraloTextSecondary,
            )
        }
    }
}
