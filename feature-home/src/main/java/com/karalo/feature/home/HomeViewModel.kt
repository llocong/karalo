package com.karalo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.feature.search.domain.SearchResultItem
import com.karalo.feature.search.domain.SearchYouTubeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TOP_PICKS_QUERY = "karaoke"
private const val POP_QUERY = "karaoke pop"
private const val ROCK_QUERY = "karaoke rock"

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val searchYouTube: SearchYouTubeUseCase,
        private val searchSessionHolder: SearchSessionHolder,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        init {
            loadShelf(TOP_PICKS_QUERY) { state -> _uiState.update { it.copy(topPicks = state) } }
            loadShelf(POP_QUERY) { state -> _uiState.update { it.copy(pop = state) } }
            loadShelf(ROCK_QUERY) { state -> _uiState.update { it.copy(rock = state) } }
        }

        /** Sets up the player's Next/Previous queue from the shelf the user picked an item from. */
        fun onResultClicked(shelfItems: List<SearchResultItem>) {
            searchSessionHolder.setLastResults(shelfItems.map { it.toPlayableItemRef() })
        }

        private fun loadShelf(
            query: String,
            updateState: (ShelfUiState) -> Unit,
        ) {
            viewModelScope.launch {
                when (val result = searchYouTube(query)) {
                    is AppResult.Success -> updateState(ShelfUiState.Loaded(result.data))
                    is AppResult.Failure -> {
                        logger.log("Home shelf failed for \"$query\": ${result.error}")
                        updateState(ShelfUiState.Error)
                    }
                }
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
