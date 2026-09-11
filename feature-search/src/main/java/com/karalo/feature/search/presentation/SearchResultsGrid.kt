package com.karalo.feature.search.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    focusFirstItemTrigger: Int = 0,
    restoreFocusVideoId: String? = null,
    restoreFocusRequester: FocusRequester? = null,
    canRestoreFocus: Boolean = false,
    railFocusRequester: FocusRequester? = null,
) {
    val gridState = rememberLazyGridState()

    SearchResultsGridFocusEffects(
        items = items,
        gridState = gridState,
        firstItemFocusRequester = firstItemFocusRequester,
        focusFirstItemTrigger = focusFirstItemTrigger,
        restoreFocusVideoId = restoreFocusVideoId,
        restoreFocusRequester = restoreFocusRequester,
        canRestoreFocus = canRestoreFocus,
    )

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
            // Deliberately no focusRestorer() here: every focus decision this grid needs is
            // already handled explicitly above (fresh results, an explicit rail select, and
            // restoring the last-played item), and focusRestorer()'s own "remember and restore the
            // last-focused child" behavior raced with (and sometimes won over) our own explicit
            // firstItemFocusRequester.requestFocus() call above -- e.g. reselecting Search from the
            // rail after scrolling away and back would silently land back on whatever was
            // previously focused instead of the first item, since the drawer's own collapse
            // triggers a default focus-search that focusRestorer() intercepts.
            modifier = modifier.focusGroup(),
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

/**
 * The two focus-restoration effects this grid needs, kept out of [SearchResultsGrid] itself purely
 * to keep that function's own complexity down.
 */
@Composable
private fun SearchResultsGridFocusEffects(
    items: List<SearchResultItem>,
    gridState: LazyGridState,
    firstItemFocusRequester: FocusRequester?,
    focusFirstItemTrigger: Int,
    restoreFocusVideoId: String?,
    restoreFocusRequester: FocusRequester?,
    canRestoreFocus: Boolean,
) {
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

    // An explicit rail-click selection (or RIGHT-as-OK on a focused rail item) while results are
    // already showing should always bring the first result back into view and focus it -- even if
    // the grid has since been scrolled well past it. Unlike Home's shelves, which lay out their
    // outer container with a plain, non-lazy verticalScroll and so never dispose anything, this is
    // a genuinely lazy LazyVerticalGrid: a bare requestFocus() on an already-scrolled-away first
    // item is a silent no-op, exactly like the restore case just above.
    //
    // Gated on a genuinely fresh (not-yet-consumed) trigger, persisted across this composable's
    // own dispose/recreate cycle via rememberSaveable, rather than just "trigger > 0" -- a plain
    // remount for an unrelated reason (e.g. a genuine return from the player, which recreates this
    // whole grid too) would otherwise be indistinguishable from an explicit select, since
    // focusFirstItemTrigger carries the same already-seen value forward from before: without this,
    // that remount would re-run this effect and steal focus onto the first item, racing with (and
    // beating) the last-played-item restore effect above.
    var consumedFocusFirstItemTrigger by rememberSaveable { mutableIntStateOf(0) }
    if (firstItemFocusRequester != null && focusFirstItemTrigger > consumedFocusFirstItemTrigger) {
        LaunchedEffect(focusFirstItemTrigger) {
            consumedFocusFirstItemTrigger = focusFirstItemTrigger
            gridState.scrollToItem(0)
            firstItemFocusRequester.requestFocus()
        }
    }
}
