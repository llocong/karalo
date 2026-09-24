package com.karalo.feature.home

import com.karalo.feature.search.domain.SearchResultItem

/** State of a single horizontally-scrollable shelf of results. */
sealed interface ShelfUiState {
    data object Loading : ShelfUiState

    data class Loaded(
        val items: List<SearchResultItem>,
    ) : ShelfUiState

    data object Error : ShelfUiState
}

/**
 * Home shows a single shelf: Top Picks, or Halloween Hits while the Halloween theme is on. Each is
 * only searched once its theme is first shown -- see HomeViewModel.onSeasonalThemeChanged.
 */
data class HomeUiState(
    val topPicks: ShelfUiState = ShelfUiState.Loading,
    val halloween: ShelfUiState = ShelfUiState.Loading,
)
