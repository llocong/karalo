package com.karalo.feature.search.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

// Matches Home's shelf cards (see HomeScreen.kt's SHELF_CARD_WIDTH/SHELF_CARD_GUTTER) so results
// scroll the same way as any Home shelf, per the app's one consistent "browse a row of videos"
// pattern -- results just happen to be a single, ungrouped row instead of several categorized ones.
private val RESULT_CARD_WIDTH = 240.dp
private val RESULT_CARD_GUTTER = 20.dp
private val ROW_VERTICAL_PADDING = 20.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultsRow(
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
    val listState = rememberLazyListState()

    SearchResultsRowFocusEffects(
        items = items,
        listState = listState,
        firstItemFocusRequester = firstItemFocusRequester,
        focusFirstItemTrigger = focusFirstItemTrigger,
        restoreFocusVideoId = restoreFocusVideoId,
        restoreFocusRequester = restoreFocusRequester,
        canRestoreFocus = canRestoreFocus,
    )

    // Same YouTube-on-Google-TV-style carousel scrolling as the Home shelves: keeps the focused
    // card centered horizontally while scrolling through the middle of the results, pinned at the
    // start/end for the first/last couple of cards. See CenteredBringIntoViewSpec.
    CompositionLocalProvider(LocalBringIntoViewSpec provides CenteredBringIntoViewSpec) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(vertical = ROW_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(RESULT_CARD_GUTTER),
            // Deliberately no focusRestorer() here: every focus decision this row needs is already
            // handled explicitly above (fresh results, an explicit rail select, and restoring the
            // last-played item) -- see SearchResultsRowFocusEffects' own comments for why adding it
            // back would reintroduce a focus race.
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
                // Pressing LEFT on the first card always opens the drawer with the Search item
                // focused, overriding Compose's default focus search (which would otherwise land
                // on whichever rail item happens to sit spatially closest).
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
                FocusableCard(
                    title = formatVideoTitle(item.title),
                    subtitle = null,
                    thumbnailUrl = item.thumbnailUrl,
                    durationSeconds = item.durationSeconds,
                    onClick = { onResultClick(index, item.videoId) },
                    modifier = Modifier.width(RESULT_CARD_WIDTH).then(focusModifier),
                )
            }
        }
    }
}

/**
 * The two focus-restoration effects this row needs, kept out of [SearchResultsRow] itself purely
 * to keep that function's own complexity down.
 */
@Composable
private fun SearchResultsRowFocusEffects(
    items: List<SearchResultItem>,
    listState: LazyListState,
    firstItemFocusRequester: FocusRequester?,
    focusFirstItemTrigger: Int,
    restoreFocusVideoId: String?,
    restoreFocusRequester: FocusRequester?,
    canRestoreFocus: Boolean,
) {
    // Restoring focus to an item further along the row than what's initially composed requires
    // scrolling it into view first -- requestFocus() on a FocusRequester with no attached node
    // (e.g. an item the row hasn't composed yet) is a silent no-op.
    if (canRestoreFocus && restoreFocusRequester != null && restoreFocusVideoId != null) {
        val restoreTargetIndex = items.indexOfFirst { it.videoId == restoreFocusVideoId }
        LaunchedEffect(restoreFocusVideoId, restoreTargetIndex) {
            if (restoreTargetIndex >= 0) {
                listState.scrollToItem(restoreTargetIndex)
                restoreFocusRequester.requestFocus()
            }
        }
    }

    // An explicit rail-click selection (or RIGHT-as-OK on a focused rail item) while results are
    // already showing should always bring the first result back into view and focus it -- even if
    // the row has since been scrolled well past it. This is a genuinely lazy LazyRow: a bare
    // requestFocus() on an already-scrolled-away first item is a silent no-op, exactly like the
    // restore case just above.
    //
    // Gated on a genuinely fresh (not-yet-consumed) trigger, persisted across this composable's
    // own dispose/recreate cycle via rememberSaveable, rather than just "trigger > 0" -- a plain
    // remount for an unrelated reason (e.g. a genuine return from the player, which recreates this
    // whole row too) would otherwise be indistinguishable from an explicit select, since
    // focusFirstItemTrigger carries the same already-seen value forward from before: without this,
    // that remount would re-run this effect and steal focus onto the first item, racing with (and
    // beating) the last-played-item restore effect above.
    var consumedFocusFirstItemTrigger by rememberSaveable { mutableIntStateOf(0) }
    if (firstItemFocusRequester != null && focusFirstItemTrigger > consumedFocusFirstItemTrigger) {
        LaunchedEffect(focusFirstItemTrigger) {
            consumedFocusFirstItemTrigger = focusFirstItemTrigger
            listState.scrollToItem(0)
            firstItemFocusRequester.requestFocus()
        }
    }
}
