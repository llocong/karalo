package com.karalo.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.theme.KaraloPageHeaderHeight
import com.karalo.core.ui.theme.KaraloPagePadding

private val SAFE_ZONE_HORIZONTAL = KaraloPagePadding
private val SAFE_ZONE_VERTICAL = KaraloPagePadding

// Sized after YouTube's own TV History page: 36dp pills, 18dp icons, roomy side padding.
private val COMPACT_BUTTON_HEIGHT = 36.dp

// Roomier than the header pills: these are full-width, standalone choices in the Clear panel.
private val PANEL_BUTTON_PADDING = PaddingValues(horizontal = 28.dp, vertical = 16.dp)
internal const val NEUTRAL_FILL_ALPHA = 0.12f
private const val DISABLED_FILL_ALPHA = 0.05f
private const val DISABLED_CONTENT_ALPHA = 0.38f

@Composable
internal fun HistoryHeader(
    uiState: HistoryUiState,
    hasSongs: Boolean,
    onSortClick: () -> Unit,
    onPauseClick: () -> Unit,
    onClearClick: () -> Unit,
    focus: HistoryFocus,
    railFocusRequester: FocusRequester?,
) {
    // Sort and Clear all are disabled once there's nothing listed (e.g. right after clearing), but a
    // button disabled while it holds focus keeps it -- so hand focus to Pause/Resume, the one
    // button that's always enabled, instead of leaving it on a dead button.
    var sortFocused by remember { mutableStateOf(false) }
    var clearFocused by remember { mutableStateOf(false) }
    // Keyed on the focus flags too: focus can also land on one of them just after it was disabled.
    LaunchedEffect(hasSongs, sortFocused, clearFocused) {
        if (!hasSongs && (sortFocused || clearFocused)) focus.pauseButton.requestFocus()
    }

    // Title on the left, compact actions on the right of the same line -- the layout of YouTube's
    // own TV History page, so the list gets the rest of the screen.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SAFE_ZONE_HORIZONTAL)
                .padding(bottom = 16.dp)
                .heightIn(min = KaraloPageHeaderHeight),
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeaderButton(
                text = uiState.sort.label,
                icon = Icons.Filled.Sort,
                onClick = onSortClick,
                enabled = hasSongs,
                modifier =
                    Modifier
                        .focusRequester(focus.sortButton)
                        .onFocusChanged { sortFocused = it.isFocused }
                        .testTag(HISTORY_TAG_SORT)
                        .leftGoesTo(railFocusRequester),
            )
            HeaderButton(
                text = if (uiState.paused) "Resume history" else "Pause history",
                icon = if (uiState.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                onClick = onPauseClick,
                modifier =
                    Modifier
                        .focusRequester(focus.pauseButton)
                        .testTag(HISTORY_TAG_PAUSE)
                        // The only enabled button when there's nothing listed: it owns LEFT (to the
                        // rail) and stays put on RIGHT -- tv-material keeps a disabled button
                        // focusable, so RIGHT would otherwise hop onto Clear all and back (a
                        // visible flicker of this button's focused colors).
                        .then(if (hasSongs) Modifier else Modifier.leftGoesTo(railFocusRequester).rightStaysPut()),
            )
            HeaderButton(
                text = "Clear all",
                icon = Icons.Filled.DeleteOutline,
                onClick = onClearClick,
                enabled = hasSongs,
                modifier =
                    Modifier
                        .focusRequester(focus.clearButton)
                        .onFocusChanged { clearFocused = it.isFocused }
                        .testTag(HISTORY_TAG_CLEAR),
            )
        }
    }
}

@Composable
private fun HeaderButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(COMPACT_BUTTON_HEIGHT),
        contentPadding = PaddingValues(start = 12.dp, end = 18.dp),
        colors = historyButtonColors(),
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        // One line even while the expanded rail narrows the page; the row just runs off the edge.
        Text(text = text, style = HEADER_BUTTON_TEXT, maxLines = 1, softWrap = false)
    }
}

// labelMedium's SemiBold (no extra tracking) at 12sp: measured against YouTube's own TV History
// pills on the same screen, that gives the same cap height (~16px at 1080p). labelSmall is Bold
// and letter-spaced, which reads wider and heavier.
internal val HEADER_BUTTON_TEXT: TextStyle
    @Composable get() =
        MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

// The same style a step larger for the side panels' options and buttons, which have the room and
// are read on their own rather than as a row of chips.
internal val PANEL_OPTION_TEXT: TextStyle
    @Composable get() = HEADER_BUTTON_TEXT.copy(fontSize = 15.sp, lineHeight = 20.sp)

/**
 * Neutral grey at rest and Hot Coral when focused, for every button on this page -- the brand's
 * accent marks focus, instead of filling the page with violet.
 */
@Composable
internal fun historyButtonColors() =
    ButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = NEUTRAL_FILL_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.secondary,
        focusedContentColor = MaterialTheme.colorScheme.onSecondary,
        pressedContainerColor = MaterialTheme.colorScheme.secondary,
        pressedContentColor = MaterialTheme.colorScheme.onSecondary,
        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_FILL_ALPHA),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
    )

@Composable
internal fun HistoryEmptyState(paused: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = SAFE_ZONE_HORIZONTAL, vertical = SAFE_ZONE_VERTICAL),
    ) {
        Text(
            text = if (paused) "Song history is paused" else "No history yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text =
                if (paused) {
                    "Resume history to start recording played songs."
                } else {
                    "Your played songs will appear here."
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun SortPanelContent(
    current: HistorySort,
    requesters: Map<HistorySort, FocusRequester>,
    onSelect: (HistorySort) -> Unit,
) {
    Text(
        text = "Sort history",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(24.dp))
    HistorySort.entries.forEach { option ->
        SortOptionRow(
            label = option.label,
            selected = option == current,
            onClick = { onSelect(option) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).focusRequester(requesters.getValue(option)),
        )
    }
}

@Composable
internal fun ClearPanelContent(
    cancelRequester: FocusRequester,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Text(
        text = "Clear song history?",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "This will permanently remove all songs from your history.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
    )
    Button(
        onClick = onCancel,
        colors = historyButtonColors(),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PANEL_BUTTON_PADDING,
        modifier = Modifier.fillMaxWidth().focusRequester(cancelRequester).testTag(HISTORY_TAG_CANCEL),
    ) { Text(text = "Cancel", style = PANEL_OPTION_TEXT) }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onConfirm,
        colors = historyButtonColors(),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PANEL_BUTTON_PADDING,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text = "Confirm", style = PANEL_OPTION_TEXT) }
}
