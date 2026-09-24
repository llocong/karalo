package com.karalo.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.karaoke.domain.HistoryPlay
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.core.karaoke.domain.MostPlayedSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

internal const val HISTORY_PAGE_SIZE = 50
private const val LOAD_ERROR_MESSAGE = "Couldn't load your song history. Check your connection and try again."

/**
 * Backs the History page. Pages are fetched on demand as the list scrolls (see [loadMore]), never
 * all at once, so an unlimited history stays cheap; only what has been scrolled past is held here.
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val karaokeRepository: KaraokeRepository,
        private val searchSessionHolder: SearchSessionHolder,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HistoryUiState())
        val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

        // Visible for tests, so date headings don't depend on the machine's time zone.
        internal var zone: ZoneId = ZoneId.systemDefault()
        internal var today: () -> LocalDate = { LocalDate.now(zone) }

        private var plays: List<HistoryPlay> = emptyList()
        private var songs: List<MostPlayedSong> = emptyList()
        private var nextCursor: String? = null
        private var nextOffset: Int? = null
        private var loadJob: Job? = null
        private var hasLoaded = false

        init {
            // A song finishing (or a Play Now ending) changes now-playing right after the backend
            // records it -- reload then, since the page's own refresh on return from the player
            // can run a moment before the player has reported that last play.
            viewModelScope.launch {
                karaokeRepository.queueSnapshot
                    .map { it.nowPlaying?.videoId }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { if (hasLoaded) refresh() }
            }
        }

        /**
         * (Re-)fetches from the top -- called whenever the page comes into view (the first load
         * included, so nothing is fetched until History is actually opened), since new plays
         * may have landed. Reloads at least as many entries as were already loaded, so the list
         * (and wherever focus was in it) doesn't shrink back to the first page.
         */
        fun refresh() = reload(keepCount = loadedCount())

        fun retry() = reload(keepCount = 0)

        fun loadMore() {
            val state = _uiState.value
            val busy = loadJob?.isActive == true || state.isLoading
            if (busy || state.endReached || state.errorMessage != null) return
            _uiState.update { it.copy(isLoadingMore = true) }
            loadJob =
                viewModelScope.launch {
                    val ok = fetchNextPage()
                    _uiState.update { it.copy(isLoadingMore = false) }
                    if (ok) publishRows()
                }
        }

        fun openSortPanel() = _uiState.update { it.copy(panel = HistoryPanel.SORT) }

        fun openClearPanel() = _uiState.update { it.copy(panel = HistoryPanel.CLEAR_CONFIRM) }

        fun dismissPanel() = _uiState.update { it.copy(panel = HistoryPanel.NONE) }

        fun setSort(sort: HistorySort) {
            val changed = sort != _uiState.value.sort
            _uiState.update { it.copy(sort = sort, panel = HistoryPanel.NONE) }
            if (changed) {
                _uiState.update { it.copy(rows = emptyList()) }
                reload(keepCount = 0)
            }
        }

        fun togglePaused() {
            val target = !_uiState.value.paused
            viewModelScope.launch {
                val result = karaokeRepository.setHistoryPaused(target)
                if (result is AppResult.Success) _uiState.update { it.copy(paused = result.data) }
            }
        }

        fun confirmClear() {
            _uiState.update { it.copy(panel = HistoryPanel.NONE) }
            viewModelScope.launch {
                if (karaokeRepository.clearHistory() is AppResult.Success) {
                    loadJob?.cancel()
                    plays = emptyList()
                    songs = emptyList()
                    nextCursor = null
                    nextOffset = null
                    _uiState.update {
                        it.copy(
                            rows = emptyList(),
                            endReached = true,
                            isLoading = false,
                            isLoadingMore = false,
                        )
                    }
                }
            }
        }

        /** Hands the song to the player the same way Home/Search do; the play itself is recorded as Play Now. */
        fun onSongSelected(song: HistoryRow.Song) {
            searchSessionHolder.setLastResults(
                listOf(
                    PlayableItemRef(song.videoId, song.title, song.channelName, song.thumbnailUrl, durationMs = null),
                ),
            )
        }

        private fun loadedCount(): Int =
            when (_uiState.value.sort) {
                HistorySort.BY_DATE -> plays.size
                HistorySort.MOST_PLAYED -> songs.size
            }

        private fun reload(keepCount: Int) {
            loadJob?.cancel()
            _uiState.update { it.copy(isLoading = it.rows.isEmpty(), isLoadingMore = false, errorMessage = null) }
            loadJob =
                viewModelScope.launch {
                    plays = emptyList()
                    songs = emptyList()
                    nextCursor = null
                    nextOffset = null
                    _uiState.update { it.copy(endReached = false) }
                    var ok = fetchNextPage()
                    while (ok && !_uiState.value.endReached && loadedCount() < keepCount) ok = fetchNextPage()
                    if (ok) {
                        hasLoaded = true
                        _uiState.update { it.copy(isLoading = false) }
                        publishRows()
                    } else {
                        // A failed background refresh keeps what's on screen; only an empty page shows the error.
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = if (it.rows.isEmpty()) LOAD_ERROR_MESSAGE else null,
                            )
                        }
                    }
                }
        }

        /** Appends the next page to the loaded items; false if the request failed. */
        private suspend fun fetchNextPage(): Boolean =
            when (_uiState.value.sort) {
                HistorySort.BY_DATE -> {
                    when (val result = karaokeRepository.historyByDate(nextCursor, HISTORY_PAGE_SIZE)) {
                        is AppResult.Success -> {
                            plays = plays + result.data.items
                            nextCursor = result.data.next
                            _uiState.update {
                                it.copy(
                                    paused = result.data.paused,
                                    endReached = result.data.next == null,
                                )
                            }
                            true
                        }
                        is AppResult.Failure -> false
                    }
                }
                HistorySort.MOST_PLAYED -> {
                    when (val result = karaokeRepository.mostPlayed(nextOffset ?: 0, HISTORY_PAGE_SIZE)) {
                        is AppResult.Success -> {
                            songs = songs + result.data.items
                            nextOffset = result.data.next
                            _uiState.update {
                                it.copy(
                                    paused = result.data.paused,
                                    endReached = result.data.next == null,
                                )
                            }
                            true
                        }
                        is AppResult.Failure -> false
                    }
                }
            }

        private fun publishRows() {
            val rows =
                when (_uiState.value.sort) {
                    HistorySort.BY_DATE -> rowsByDate(plays, zone, today())
                    HistorySort.MOST_PLAYED -> rowsByPlayCount(songs)
                }
            _uiState.update { it.copy(rows = rows) }
        }
    }
