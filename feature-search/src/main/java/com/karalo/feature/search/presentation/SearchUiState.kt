package com.karalo.feature.search.presentation

import com.karalo.feature.search.domain.SearchResultItem

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Suggesting(val rawQuery: String, val suggestions: List<String>) : SearchUiState
    data class Loading(val rawQuery: String) : SearchUiState
    data class Results(val rawQuery: String, val items: List<SearchResultItem>) : SearchUiState
    data class Error(val rawQuery: String, val message: String) : SearchUiState
}
