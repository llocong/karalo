package com.karalo.feature.search.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.focus.CenteredBringIntoViewSpec

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
 * Home's shelves and [SearchResultsRow], just applied to text chips sized to their own content
 * instead of fixed-width video cards.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SuggestionChipsRow(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    firstItemFocusRequester: FocusRequester,
    focusFirstItemTrigger: Int = 0,
    queryFieldFocusRequester: FocusRequester?,
    railFocusRequester: FocusRequester?,
) {
    if (suggestions.isEmpty()) return
    val listState = rememberLazyListState()

    // Pressing DOWN from the query field bumps this to ask for the first chip -- a bare
    // requestFocus() on it (see the modifier below) is a silent no-op once the row has been
    // scrolled away from index 0 (a genuinely lazy LazyRow disposes off-screen chips), e.g. having
    // arrowed right through suggestions, then back up to the field, then down again. Scrolling
    // back to 0 first, exactly like SearchResultsRow's own focusFirstItemTrigger handling, fixes
    // that. No rememberSaveable-persisted "consumed" bookkeeping needed here (unlike that one):
    // this trigger is purely a same-session keypress echo, not something that can go stale across
    // an external remount, so a plain per-composition counter starting fresh at 0 is enough.
    LaunchedEffect(focusFirstItemTrigger) {
        if (focusFirstItemTrigger > 0) {
            listState.scrollToItem(0)
            firstItemFocusRequester.requestFocus()
        }
    }

    // Same YouTube-on-Google-TV-style carousel scrolling as the Home shelves and SearchResultsRow:
    // keeps the focused chip centered while scrolling through the middle of the row, pinned at the
    // start/end for the first/last couple of chips.
    CompositionLocalProvider(LocalBringIntoViewSpec provides CenteredBringIntoViewSpec) {
        LazyRow(
            state = listState,
            contentPadding =
                PaddingValues(
                    start = SAFE_ZONE_HORIZONTAL,
                    end = SAFE_ZONE_HORIZONTAL,
                    top = SUGGESTIONS_ROW_TOP_GAP,
                    bottom = CHIP_ROW_BOTTOM_PADDING,
                ),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GUTTER),
            modifier =
                Modifier
                    .focusGroup()
                    // UP from any chip always returns to the query field -- the search bar now has
                    // two focusable siblings (mic button, field), so Compose's default spatial
                    // search could ambiguously resolve to either; made deterministic here instead,
                    // on one ancestor covering every chip rather than duplicated per item.
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionUp &&
                            queryFieldFocusRequester != null
                        ) {
                            queryFieldFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
        ) {
            itemsIndexed(suggestions) { index, suggestion ->
                var focusModifier: Modifier = Modifier
                if (index == 0) {
                    focusModifier = focusModifier.focusRequester(firstItemFocusRequester)
                }
                // Pressing LEFT on the first chip always opens the drawer with the Search item
                // focused, overriding Compose's default focus search (matching SearchResultsRow's
                // identical first-item convention).
                if (index == 0 && railFocusRequester != null) {
                    focusModifier =
                        focusModifier.onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                                railFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                }
                SuggestionChip(
                    label = formatSuggestion(suggestion).lowercase(),
                    onClick = { onSuggestionClick(suggestion) },
                    modifier = focusModifier,
                )
            }
        }
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
