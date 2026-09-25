package com.karalo.feature.playlists

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

@HiltViewModel
class PlaylistsViewModel
    @Inject
    constructor(
        private val searchYouTube: SearchYouTubeUseCase,
        private val searchSessionHolder: SearchSessionHolder,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PlaylistsUiState())
        val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

        private val requested = mutableSetOf<String>()

        init {
            onPlaylistFocused(0)
        }

        /**
         * Switches the carousel to [index] straight away, and searches its songs if that hasn't
         * happened yet -- plus its two neighbours', so the next LEFT/RIGHT usually lands on songs
         * that are already there.
         */
        fun onPlaylistFocused(index: Int) {
            val playlists = _uiState.value.playlists
            if (index !in playlists.indices) return
            _uiState.update { it.copy(selectedIndex = index) }
            for (i in listOf(index, index + 1, index - 1)) {
                playlists.getOrNull(i)?.let(::load)
            }
        }

        /** Sets up the player's Next/Previous queue from the playlist the user picked a song from. */
        fun onResultClicked(items: List<SearchResultItem>) {
            searchSessionHolder.setLastResults(items.map { it.toPlayableItemRef() })
        }

        private fun load(playlist: Playlist) {
            if (!requested.add(playlist.id)) return
            // A retry after an error shows the spinner again rather than the stale error.
            _uiState.update { state ->
                if (state.songs[playlist.id] == PlaylistSongsState.Error) {
                    state.copy(songs = state.songs - playlist.id)
                } else {
                    state
                }
            }
            viewModelScope.launch {
                val state =
                    when (val result = searchYouTube(playlist.query)) {
                        is AppResult.Success -> PlaylistSongsState.Loaded(result.data)
                        is AppResult.Failure -> {
                            logger.log("Playlist \"${playlist.name}\" failed: ${result.error}")
                            // Retried the next time it's focused.
                            requested.remove(playlist.id)
                            PlaylistSongsState.Error
                        }
                    }
                _uiState.update { it.copy(songs = it.songs + (playlist.id to state)) }
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
