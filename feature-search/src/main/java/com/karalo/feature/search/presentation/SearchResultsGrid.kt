package com.karalo.feature.search.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.core.ui.focus.CenteredBringIntoViewSpec
import com.karalo.feature.search.domain.SearchResultItem

private const val GRID_COLUMNS = 3

// 20dp gutters between grid items, per the TV layout guidelines' 12-column grid spec
// (developer.android.com/design/ui/tv/guides/styles/layouts).
private val GRID_GUTTER = 20.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultsGrid(
    items: List<SearchResultItem>,
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    restoreFocusVideoId: String? = null,
    restoreFocusRequester: FocusRequester? = null,
    canRestoreFocus: Boolean = false,
    railFocusRequester: FocusRequester? = null,
) {
    val gridState = rememberLazyGridState()

    // Restoring focus to an item further down the grid than what's initially composed requires
    // scrolling it into view first -- requestFocus() on a FocusRequester with no attached node
    // (e.g. an item the grid hasn't composed yet) is a silent no-op.
    if (canRestoreFocus && restoreFocusRequester != null && restoreFocusVideoId != null) {
        val restoreTargetIndex = items.indexOfFirst { it.videoId == restoreFocusVideoId }
        LaunchedEffect(restoreFocusVideoId, restoreTargetIndex) {
            if (restoreTargetIndex >= 0) {
                gridState.scrollToItem(restoreTargetIndex)
                restoreFocusRequester.requestFocus()
            }
        }
    }

    // Same YouTube-on-Google-TV-style carousel scrolling as the Home shelves: keeps the focused
    // row centered vertically while scrolling through the middle of the results, pinned at the
    // start/end for the first/last couple of rows. See CenteredBringIntoViewSpec.
    CompositionLocalProvider(LocalBringIntoViewSpec provides CenteredBringIntoViewSpec) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_GUTTER),
            verticalArrangement = Arrangement.spacedBy(GRID_GUTTER),
            modifier = modifier.focusGroup().focusRestorer(),
        ) {
            itemsIndexed(items) { index, item ->
                var focusModifier: Modifier = Modifier
                if (index == 0 && firstItemFocusRequester != null) {
                    focusModifier = focusModifier.focusRequester(firstItemFocusRequester)
                }
                if (restoreFocusRequester != null && item.videoId == restoreFocusVideoId) {
                    focusModifier = focusModifier.focusRequester(restoreFocusRequester)
                }
                // Pressing LEFT on any card in the leftmost column always opens the drawer with
                // the Search item focused, overriding Compose's default focus search (which would
                // otherwise land on whichever rail item happens to sit spatially closest to that
                // row).
                if (index % GRID_COLUMNS == 0 && railFocusRequester != null) {
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
                FocusableCard(
                    title = formatVideoTitle(item.title),
                    subtitle = null,
                    thumbnailUrl = item.thumbnailUrl,
                    durationSeconds = item.durationSeconds,
                    onClick = { onResultClick(index, item.videoId) },
                    modifier = focusModifier,
                )
            }
        }
    }
}
