package com.karalo.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.core.ui.components.LoadingIndicator
import com.karalo.feature.search.domain.SearchResultItem
import kotlinx.coroutines.launch

// Safe-zone content margins recommended by the TV layout guidelines
// (developer.android.com/design/ui/tv/guides/styles/layouts). The bottom gets extra breathing
// room on top of that so the last shelf's focused (scaled-up) card never touches the screen edge.
private val SAFE_ZONE_HORIZONTAL = 58.dp
private val SAFE_ZONE_VERTICAL = 28.dp
private val SAFE_ZONE_BOTTOM_EXTRA = 24.dp
private val BOTTOM_SPACER_HEIGHT = SAFE_ZONE_VERTICAL + SAFE_ZONE_BOTTOM_EXTRA

private val SHELF_SPACING = 32.dp
private val SHELF_TITLE_SPACING = 20.dp
private val SHELF_CARD_WIDTH = 240.dp
private val SHELF_CARD_GUTTER = 20.dp
private val SHELF_ROW_VERTICAL_PADDING = 20.dp
private val SHELF_LOADING_HEIGHT = 200.dp

private const val TOP_PICKS_TITLE = "Top Picks"
private const val POP_TITLE = "Pop"
private const val ROCK_TITLE = "Rock"

/**
 * A [BringIntoViewSpec] that pivots on the *center* of both the focused card and the viewport,
 * producing YouTube-on-Google-TV-style carousel scrolling: the focused card is kept centered while
 * scrolling through the middle of the row. This mirrors the shape of the platform's own internal
 * `PivotBringIntoViewSpec` (used by default on TV via [LocalBringIntoViewSpec], but package-private
 * so not reusable here) except with both the "parent" and "child" pivot fractions at 0.5 instead of
 * 0.3/0 -- i.e. the item's own center aligned to the viewport's center, not its leading edge
 * aligned 30% in from the start.
 *
 * Because a scrollable can never actually scroll past its real content bounds, the "desired"
 * offset this produces for the first couple of cards is more negative than the row's true start
 * (clamped there, so they stay pinned at the row's own start padding), and for the last couple of
 * cards exceeds the row's max scroll extent (clamped there instead) -- giving the
 * "pinned at start / centered through the middle / pinned at end" behavior with no extra per-card
 * state or hardcoded index thresholds.
 */
@OptIn(ExperimentalFoundationApi::class)
private object CenteredBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val centeredTargetForLeadingEdge = (containerSize - size) / 2f
        // Defensive fallback mirroring the platform spec's own guard: only matters if a focused
        // card is ever wider than the viewport itself (never true for this shelf's fixed-width
        // cards on a TV-sized screen), aligning the trailing edge instead of requesting an
        // unsatisfiable centered position.
        val spaceAvailable = containerSize - centeredTargetForLeadingEdge
        return if (size <= containerSize && spaceAvailable < size) {
            offset - (containerSize - size)
        } else {
            offset - centeredTargetForLeadingEdge
        }
    }
}

@Composable
fun HomeScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onResultClick = { items, index ->
            viewModel.onResultClicked(items)
            onResultClick(index, items[index].videoId)
        },
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The top safe-zone inset goes *outside* verticalScroll so it's a fixed part of the viewport
    // rather than scrollable content -- otherwise it (and, per shelf, the title above each row)
    // can get scrolled out of reach: focus-driven auto-scroll only moves just enough to reveal
    // the newly-focused card, not the whole safe zone or the shelf's own title above it. The
    // bottom inset, though, is a trailing Spacer *inside* the scrollable content instead of a
    // matching fixed inset -- it should only ever be visible once you've scrolled to the last
    // shelf, not permanently shrink the viewport while you're still at the top.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = SAFE_ZONE_VERTICAL)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SHELF_SPACING),
    ) {
        HomeShelf(title = TOP_PICKS_TITLE, state = uiState.topPicks, onResultClick = onResultClick)
        HomeShelf(title = POP_TITLE, state = uiState.pop, onResultClick = onResultClick)
        HomeShelf(title = ROCK_TITLE, state = uiState.rock, onResultClick = onResultClick)
        Spacer(modifier = Modifier.height(BOTTOM_SPACER_HEIGHT))
    }
}

/**
 * A titled, horizontally-scrollable row of video cards -- the "Standard Card" pattern from the TV
 * cards guidelines (developer.android.com/design/ui/tv/guides/components/cards), reusing the same
 * [FocusableCard] as the search results grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeShelf(
    title: String,
    state: ShelfUiState,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    // Requests the whole shelf (title included) into view -- not just the focused card -- when
    // any card in this shelf gains focus, so scrolling back up to an earlier shelf always reveals
    // its title again rather than stopping as soon as the card itself is visible.
    val shelfBringIntoViewRequester = remember { BringIntoViewRequester() }
    val listState = rememberLazyListState()

    Column(
        modifier =
            Modifier
                .bringIntoViewRequester(shelfBringIntoViewRequester)
                .onFocusChanged {
                    if (it.hasFocus) coroutineScope.launch { shelfBringIntoViewRequester.bringIntoView() }
                },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = SAFE_ZONE_HORIZONTAL),
        )
        Spacer(modifier = Modifier.height(SHELF_TITLE_SPACING))
        when (state) {
            is ShelfUiState.Loading -> LoadingIndicator(modifier = Modifier.fillMaxWidth().height(SHELF_LOADING_HEIGHT))
            is ShelfUiState.Error ->
                Text(
                    text = "Couldn't load \"$title\". Check your connection and try again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(SHELF_LOADING_HEIGHT)
                            .padding(horizontal = SAFE_ZONE_HORIZONTAL),
                )
            is ShelfUiState.Loaded ->
                // Scoped to just this LazyRow (not the outer Column above) so it only overrides
                // the row's own horizontal focus-follow scrolling, leaving the shelf's vertical
                // bring-into-view behavior (the onFocusChanged block above) on the platform
                // default.
                CompositionLocalProvider(LocalBringIntoViewSpec provides CenteredBringIntoViewSpec) {
                    LazyRow(
                        state = listState,
                        contentPadding =
                            PaddingValues(horizontal = SAFE_ZONE_HORIZONTAL, vertical = SHELF_ROW_VERTICAL_PADDING),
                        horizontalArrangement = Arrangement.spacedBy(SHELF_CARD_GUTTER),
                    ) {
                        itemsIndexed(state.items) { index, item ->
                            FocusableCard(
                                title = formatVideoTitle(item.title),
                                subtitle = null,
                                thumbnailUrl = item.thumbnailUrl,
                                durationSeconds = item.durationSeconds,
                                onClick = { onResultClick(state.items, index) },
                                modifier = Modifier.width(SHELF_CARD_WIDTH),
                            )
                        }
                    }
                }
        }
    }
}
