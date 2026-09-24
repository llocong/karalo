package com.karalo.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val SAFE_ZONE_HORIZONTAL = 58.dp
private val SAFE_ZONE_VERTICAL = 28.dp
private const val PILL_CORNER_PERCENT = 50

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
    Column(modifier = Modifier.padding(horizontal = SAFE_ZONE_HORIZONTAL).padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (uiState.paused) PausedBadge(modifier = Modifier.padding(start = 16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
            HeaderButton(
                text = "Sort: ${uiState.sort.label}",
                icon = Icons.Filled.Sort,
                onClick = onSortClick,
                enabled = hasSongs,
                modifier =
                    Modifier
                        .focusRequester(focus.sortButton)
                        .leftGoesTo(railFocusRequester),
            )
            HeaderButton(
                text = if (uiState.paused) "Resume song history" else "Pause song history",
                icon = if (uiState.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                onClick = onPauseClick,
                modifier =
                    Modifier
                        .focusRequester(focus.pauseButton)
                        .testTag(HISTORY_TAG_PAUSE)
                        // First enabled button when there's nothing to sort, so it owns LEFT then.
                        .then(if (hasSongs) Modifier else Modifier.leftGoesTo(railFocusRequester)),
            )
            HeaderButton(
                text = "Clear history",
                icon = Icons.Filled.DeleteOutline,
                onClick = onClearClick,
                enabled = hasSongs,
                modifier = Modifier.focusRequester(focus.clearButton),
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
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        // One line even while the expanded rail narrows the page; the row just runs off the edge.
        Text(text = text, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PausedBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(PILL_CORNER_PERCENT))
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PauseCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "History paused",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

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
        modifier = Modifier.fillMaxWidth().focusRequester(cancelRequester).testTag(HISTORY_TAG_CANCEL),
    ) { Text(text = "Cancel") }
    Spacer(modifier = Modifier.height(12.dp))
    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text(text = "Confirm") }
}
