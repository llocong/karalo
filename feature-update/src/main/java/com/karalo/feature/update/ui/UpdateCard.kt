@file:Suppress("MagicNumber") // sizes and colors are the design's own values

package com.karalo.feature.update.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.KaraloPillButton
import com.karalo.core.ui.icons.KaraloIcons
import com.karalo.core.ui.theme.KaraloOnBackground
import com.karalo.core.ui.theme.KaraloOnSurfaceVariant
import com.karalo.core.ui.theme.LocalKaraloTokens
import com.karalo.feature.update.domain.UpdateState

const val UPDATE_TAG_CARD = "update_card"
const val UPDATE_TAG_PRIMARY = "update_primary"
const val UPDATE_TAG_WHATS_NEW = "update_whats_new"

// The "TV — Settings" artboard's update card, in dp (1cqw = 9.6dp).
private val CARD_SHAPE = RoundedCornerShape(13.dp)
private val CARD_BORDER = 2.dp
private val CARD_PADDING_HORIZONTAL = 19.dp
private val CARD_PADDING_VERTICAL = 17.dp
private val CARD_GAP = 15.dp
private val ROW_GAP = 17.dp
private val ICON_TILE = 40.dp
private val ICON_TILE_SHAPE = RoundedCornerShape(11.dp)
private val ICON_SIZE = 23.dp
private val BUTTON_GAP = 11.5.dp
private val PROGRESS_HEIGHT = 7.7.dp

/**
 * The update card at the top of Settings, for every state but [UpdateState.UpToDate] (where it
 * isn't shown at all). [primaryFocusRequester] is its main button (Update now, Restart now), and
 * [whatsNewFocusRequester] its What's new button, the only one while downloading.
 * [onSecondButtonFocused] reports whether focus is on a button that has another to its left, so
 * Settings can let LEFT move there instead of back to the rail.
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    installedVersion: String,
    onUpdateNow: () -> Unit,
    onRestartNow: () -> Unit,
    onWhatsNew: () -> Unit,
    primaryFocusRequester: FocusRequester,
    whatsNewFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onSecondButtonFocused: (Boolean) -> Unit = {},
) {
    val release = state.releaseOrNull ?: return
    val tokens = LocalKaraloTokens.current
    val content =
        when (state) {
            is UpdateState.Available ->
                CardContent(
                    icon = KaraloIcons.Download,
                    iconTile = tokens.accentSoft,
                    iconTint = tokens.accent,
                    title = "Version ${release.version} is available",
                    subtitle =
                        if (state.failed) "Download failed. Try again." else "You have version $installedVersion",
                    primaryLabel = "Update now",
                    onPrimary = onUpdateNow,
                )
            is UpdateState.Downloading ->
                CardContent(
                    icon = KaraloIcons.Download,
                    iconTile = tokens.accentSoft,
                    iconTint = tokens.accent,
                    title = "Downloading version ${release.version}",
                    subtitle =
                        "${state.percent}% · ${megabytes(state.downloadedBytes)} of " +
                            "${megabytes(state.totalBytes)} MB · You can keep singing",
                    primaryLabel = null,
                    onPrimary = {},
                )
            is UpdateState.ReadyToRestart ->
                CardContent(
                    icon = KaraloIcons.Restart,
                    iconTile = tokens.accent,
                    iconTint = tokens.onAccent,
                    title = "Update ready",
                    subtitle = "Restart Karalo to finish installing version ${release.version}",
                    primaryLabel = "Restart now",
                    onPrimary = onRestartNow,
                )
            UpdateState.UpToDate -> return
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        modifier =
            modifier
                .testTag(UPDATE_TAG_CARD)
                .fillMaxWidth()
                .background(tokens.surface, CARD_SHAPE)
                .border(CARD_BORDER, tokens.outline, CARD_SHAPE)
                .padding(horizontal = CARD_PADDING_HORIZONTAL, vertical = CARD_PADDING_VERTICAL)
                // Nothing above the card; keep UP from leaking out of the page.
                .onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(ICON_TILE).background(content.iconTile, ICON_TILE_SHAPE),
            ) {
                Icon(
                    content.icon,
                    contentDescription = null,
                    tint = content.iconTint,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = KaraloOnBackground,
                )
                Text(
                    text = content.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = KaraloOnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                if (content.primaryLabel != null) {
                    KaraloPillButton(
                        text = content.primaryLabel,
                        onClick = content.onPrimary,
                        primary = true,
                        modifier = Modifier.focusRequester(primaryFocusRequester).testTag(UPDATE_TAG_PRIMARY),
                    )
                }
                KaraloPillButton(
                    text = "What’s new",
                    onClick = onWhatsNew,
                    modifier =
                        Modifier
                            .focusRequester(whatsNewFocusRequester)
                            .testTag(UPDATE_TAG_WHATS_NEW)
                            .onFocusChanged { onSecondButtonFocused(it.isFocused && content.primaryLabel != null) },
                )
            }
        }
        if (state is UpdateState.Downloading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PROGRESS_HEIGHT)
                        .background(tokens.raised, CircleShape),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(state.percent / 100f)
                            .background(tokens.accent, CircleShape),
                )
            }
        }
    }
}

private class CardContent(
    val icon: ImageVector,
    val iconTile: Color,
    val iconTint: Color,
    val title: String,
    val subtitle: String,
    val primaryLabel: String?,
    val onPrimary: () -> Unit,
)
