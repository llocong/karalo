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

data class HomeUiState(
    val topPicks: ShelfUiState = ShelfUiState.Loading,
    val pop: ShelfUiState = ShelfUiState.Loading,
    val rock: ShelfUiState = ShelfUiState.Loading,
)
