package com.karalo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.core.ui.components.LoadingIndicator
import com.karalo.feature.search.domain.SearchResultItem

// Safe-zone content margins recommended by the TV layout guidelines
// (developer.android.com/design/ui/tv/guides/styles/layouts).
private val SAFE_ZONE_HORIZONTAL = 58.dp
private val SAFE_ZONE_VERTICAL = 28.dp

private val SHELF_SPACING = 32.dp
private val SHELF_CARD_WIDTH = 240.dp
private val SHELF_CARD_GUTTER = 20.dp
private val SHELF_LOADING_HEIGHT = 200.dp

private const val TOP_PICKS_TITLE = "Top Picks"
private const val POP_TITLE = "Pop"
private const val ROCK_TITLE = "Rock"

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
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = SAFE_ZONE_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(SHELF_SPACING),
    ) {
        HomeShelf(title = TOP_PICKS_TITLE, state = uiState.topPicks, onResultClick = onResultClick)
        HomeShelf(title = POP_TITLE, state = uiState.pop, onResultClick = onResultClick)
        HomeShelf(title = ROCK_TITLE, state = uiState.rock, onResultClick = onResultClick)
    }
}

/**
 * A titled, horizontally-scrollable row of video cards -- the "Standard Card" pattern from the TV
 * cards guidelines (developer.android.com/design/ui/tv/guides/components/cards), reusing the same
 * [FocusableCard] as the search results grid.
 */
@Composable
private fun HomeShelf(
    title: String,
    state: ShelfUiState,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = SAFE_ZONE_HORIZONTAL),
        )
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
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SAFE_ZONE_HORIZONTAL, vertical = 12.dp),
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
