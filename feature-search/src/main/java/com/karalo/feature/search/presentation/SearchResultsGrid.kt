package com.karalo.feature.search.presentation

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.feature.search.domain.SearchResultItem

private const val GRID_COLUMNS = 4

@Composable
fun SearchResultsGrid(
    items: List<SearchResultItem>,
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        modifier = modifier.focusGroup().focusRestorer(),
    ) {
        itemsIndexed(items) { index, item ->
            FocusableCard(
                title = formatVideoTitle(item.title),
                subtitle = null,
                thumbnailUrl = item.thumbnailUrl,
                onClick = { onResultClick(index, item.videoId) },
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
