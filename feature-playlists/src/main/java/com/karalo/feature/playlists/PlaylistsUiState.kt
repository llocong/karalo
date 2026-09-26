package com.karalo.feature.playlists

import com.karalo.feature.search.domain.SearchResultItem

/** Songs of one playlist -- searched the first time it's focused (or is next to the focused one). */
sealed interface PlaylistSongsState {
    data object Loading : PlaylistSongsState

    data class Loaded(
        val items: List<SearchResultItem>,
    ) : PlaylistSongsState

    data object Error : PlaylistSongsState
}

data class PlaylistsUiState(
    val playlists: List<Playlist> = PLAYLISTS,
    /** The playlist the row's focus is on (or was last on) -- whose songs the carousel shows. */
    val selectedIndex: Int = 0,
    val songs: Map<String, PlaylistSongsState> = emptyMap(),
) {
    val selected: Playlist get() = playlists[selectedIndex]
    val selectedSongs: PlaylistSongsState get() = songs[selected.id] ?: PlaylistSongsState.Loading
}
