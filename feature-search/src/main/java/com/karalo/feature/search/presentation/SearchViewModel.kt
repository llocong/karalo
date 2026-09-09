package com.karalo.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.feature.search.domain.GetSearchSuggestionsUseCase
import com.karalo.feature.search.domain.SearchResultItem
import com.karalo.feature.search.domain.SearchYouTubeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUGGESTIONS_DEBOUNCE_MS = 300L
private const val GENERIC_ERROR_MESSAGE = "Couldn't load results. Check your connection and try again."

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val getSearchSuggestions: GetSearchSuggestionsUseCase,
        private val searchYouTube: SearchYouTubeUseCase,
        private val searchSessionHolder: SearchSessionHolder,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        private val queryInput = MutableStateFlow("")

        init {
            viewModelScope.launch {
                queryInput
                    .debounce(SUGGESTIONS_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collectLatest { raw -> loadSuggestions(raw) }
            }
        }

        fun onQueryChanged(rawQuery: String) {
            queryInput.value = rawQuery
            if (rawQuery.isBlank()) {
                _uiState.value = SearchUiState.Idle
            }
        }

        fun onSubmit(rawQuery: String = queryInput.value) {
            if (rawQuery.isBlank()) return
            viewModelScope.launch {
                _uiState.value = SearchUiState.Loading(rawQuery)
                when (val result = searchYouTube(rawQuery)) {
                    is AppResult.Success -> {
                        searchSessionHolder.setLastResults(result.data.map { it.toPlayableItemRef() })
                        _uiState.value = SearchUiState.Results(rawQuery, result.data)
                    }
                    is AppResult.Failure -> {
                        logger.log("Search failed for \"$rawQuery\": ${result.error}")
                        _uiState.value = SearchUiState.Error(rawQuery, GENERIC_ERROR_MESSAGE)
                    }
                }
            }
        }

        private suspend fun loadSuggestions(rawQuery: String) {
            if (rawQuery.isBlank()) return
            when (val result = getSearchSuggestions(rawQuery)) {
                is AppResult.Success -> _uiState.value = SearchUiState.Suggesting(rawQuery, result.data)
                // Suggestions are a nice-to-have — a failure here shouldn't block typing/searching.
                is AppResult.Failure -> _uiState.value = SearchUiState.Suggesting(rawQuery, emptyList())
            }
        }

        private fun SearchResultItem.toPlayableItemRef() =
            PlayableItemRef(
                videoId = videoId,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationSeconds?.times(MILLIS_PER_SECOND),
            )

        private companion object {
            const val MILLIS_PER_SECOND = 1000L
        }
    }
