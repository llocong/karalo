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

        // Guards against a stale in-flight suggestions fetch (debounced, so it can still resolve
        // shortly after onSubmit) overwriting the Results/Loading/Error state that submitting the
        // same text already produced — otherwise the UI can flicker back from "results" to
        // "suggestions" a moment after the user hits search. Cleared once the user types something
        // different, so suggestions resume for further edits.
        private var lastSubmittedQuery: String? = null

        // A second, more general guard alongside lastSubmittedQuery above: that one only catches a
        // fetch racing against a submit of the *same* text (e.g. pressing Enter on what's already
        // typed). Picking a suggestion submits *different* text than the raw query a fetch may
        // already be in flight for (e.g. typing "Eminem" starts a fetch for "Eminem", then picking
        // the "Eminem Rap God" chip before that fetch resolves) — lastSubmittedQuery alone can't
        // catch that, since the two strings never match. Incremented on every submit; a fetch only
        // applies its result if this hasn't changed since it started, i.e. nothing was submitted
        // (of *any* text) while it was in flight.
        private var submitGeneration = 0

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
            if (rawQuery != lastSubmittedQuery) {
                lastSubmittedQuery = null
            }
            if (rawQuery.isBlank()) {
                _uiState.value = SearchUiState.Idle
            }
        }

        fun onSubmit(rawQuery: String = queryInput.value) {
            if (rawQuery.isBlank()) return
            lastSubmittedQuery = rawQuery
            submitGeneration++
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
            if (rawQuery.isBlank() || rawQuery == lastSubmittedQuery) return
            val generationAtStart = submitGeneration
            val suggestions =
                when (val result = getSearchSuggestions(rawQuery)) {
                    is AppResult.Success -> result.data
                    // Suggestions are a nice-to-have — a failure here shouldn't block typing/searching.
                    is AppResult.Failure -> emptyList()
                }
            // Re-checked after the suspend call above, not just at entry: onSubmit (of this exact
            // text or, just as importantly, any other) may have happened while the fetch was in
            // flight, already moving the UI state past Suggesting — see submitGeneration's own doc.
            if (submitGeneration == generationAtStart) {
                _uiState.value = SearchUiState.Suggesting(rawQuery, suggestions)
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
