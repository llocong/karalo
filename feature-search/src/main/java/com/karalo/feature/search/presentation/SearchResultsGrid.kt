package com.karalo.feature.search.presentation

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.feature.search.domain.SearchResultItem

private const val GRID_COLUMNS = 3

// 20dp gutters between grid items, per the TV layout guidelines' 12-column grid spec
// (developer.android.com/design/ui/tv/guides/styles/layouts).
private val GRID_GUTTER = 20.dp

@Composable
fun SearchResultsGrid(
    items: List<SearchResultItem>,
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(GRID_GUTTER),
        verticalArrangement = Arrangement.spacedBy(GRID_GUTTER),
        modifier = modifier.focusGroup().focusRestorer(),
    ) {
        itemsIndexed(items) { index, item ->
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
                onClick = { onResultClick(index, item.videoId) },
                modifier = focusModifier,
            )
        }
    }
}
