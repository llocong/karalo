package com.karalo.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.core.ui.components.LoadingIndicator
import com.karalo.core.ui.focus.CenteredBringIntoViewSpec
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

@Composable
fun HomeScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    firstVideoFocusTrigger: Int = 0,
    claimInitialPlaceholderFocus: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onResultClick = { items, index ->
            viewModel.onResultClicked(items)
            onResultClick(index, items[index].videoId)
        },
        firstVideoFocusTrigger = firstVideoFocusTrigger,
        claimInitialPlaceholderFocus = claimInitialPlaceholderFocus,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
    firstVideoFocusTrigger: Int,
    claimInitialPlaceholderFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val firstVideoFocusRequester = remember { FocusRequester() }
    // Neutral focus target claimed the instant this screen mounts on the app's true first-ever
    // launch, purely to keep focus off the nav rail (Compose's fallback focus-search would
    // otherwise land there -- see KaraloNavHost) until the first shelf's own first card is ready
    // to take over below.
    val rootFocusRequester = remember { FocusRequester() }
    // Persisted across the Compose-Navigation dispose/recreate cycle that happens every time this
    // screen is re-entered (see KaraloNavHost's saveState/restoreState) so a later, unrelated
    // recomposition -- or simply re-entering Home by *focusing* it in the rail, not selecting it --
    // never mistakes an already-consumed trigger value for a fresh one.
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    val topPicksLoaded = (uiState.topPicks as? ShelfUiState.Loaded)?.takeIf { it.items.isNotEmpty() }

    if (claimInitialPlaceholderFocus) {
        LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }
    }

    // Only fires once the first shelf's data (and hence its first card) actually exists to focus.
    LaunchedEffect(firstVideoFocusTrigger, topPicksLoaded != null) {
        if (firstVideoFocusTrigger > consumedFocusTrigger && topPicksLoaded != null) {
            consumedFocusTrigger = firstVideoFocusTrigger
            firstVideoFocusRequester.requestFocus()
        }
    }

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
                .focusRequester(rootFocusRequester)
                .focusable()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SHELF_SPACING),
    ) {
        HomeShelf(
            title = TOP_PICKS_TITLE,
            state = uiState.topPicks,
            onResultClick = onResultClick,
            firstItemFocusRequester = firstVideoFocusRequester,
        )
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
    firstItemFocusRequester: FocusRequester? = null,
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
                            val focusModifier =
                                if (index == 0 && firstItemFocusRequester != null) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    Modifier
                                }
                            FocusableCard(
                                title = formatVideoTitle(item.title),
                                subtitle = null,
                                thumbnailUrl = item.thumbnailUrl,
                                durationSeconds = item.durationSeconds,
                                onClick = { onResultClick(state.items, index) },
                                modifier = Modifier.width(SHELF_CARD_WIDTH).then(focusModifier),
                            )
                        }
                    }
                }
        }
    }
}
