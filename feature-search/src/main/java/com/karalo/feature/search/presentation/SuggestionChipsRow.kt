package com.karalo.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.TvCarousel

// Safe-zone content margin, duplicated locally per this codebase's established convention (see
// HomeScreen.kt/SearchResultsRow.kt).
private val SAFE_ZONE_HORIZONTAL = 58.dp

// Gap below the search bar -- deliberately tight so the chips read as sitting right below the
// field (between it and where the results row will appear), not drift down toward mid-screen.
private val SUGGESTIONS_ROW_TOP_GAP = 24.dp
private val CHIP_ROW_BOTTOM_PADDING = 16.dp
private val CHIP_GUTTER = 12.dp
private val CHIP_HORIZONTAL_PADDING = 20.dp
private val CHIP_VERTICAL_PADDING = 10.dp

/**
 * A horizontal, pill-shaped-chip row of suggested queries -- the "browse a row" pattern shared with
 * Home's shelves and [SearchResultsRow], just applied to text chips (via [TvCarousel]'s `itemContent`
 * slot) instead of fixed-width video cards.
 */
@Composable
internal fun SuggestionChipsRow(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    firstItemFocusRequester: FocusRequester,
    focusFirstItemTrigger: Int = 0,
    queryFieldFocusRequester: FocusRequester?,
    railFocusRequester: FocusRequester?,
) {
    TvCarousel(
        items = suggestions,
        key = { it },
        contentPadding =
            PaddingValues(
                start = SAFE_ZONE_HORIZONTAL,
                end = SAFE_ZONE_HORIZONTAL,
                top = SUGGESTIONS_ROW_TOP_GAP,
                bottom = CHIP_ROW_BOTTOM_PADDING,
            ),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GUTTER),
        firstItemFocusRequester = firstItemFocusRequester,
        focusFirstItemTrigger = focusFirstItemTrigger,
        upFocusRequester = queryFieldFocusRequester,
        leftEdgeFocusRequester = railFocusRequester,
    ) { _, suggestion, itemModifier ->
        SuggestionChip(
            label = formatSuggestion(suggestion).lowercase(),
            onClick = { onSuggestionClick(suggestion) },
            modifier = itemModifier,
        )
    }
}

@Composable
private fun SuggestionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        color = if (isFocused) SearchChipFocusedText else SearchChipUnfocusedText,
        modifier =
            modifier
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
                .background(
                    if (isFocused) SearchChipFocusedBackground else SearchPillFill,
                    RoundedCornerShape(percent = 50),
                ).padding(horizontal = CHIP_HORIZONTAL_PADDING, vertical = CHIP_VERTICAL_PADDING),
    )
}
