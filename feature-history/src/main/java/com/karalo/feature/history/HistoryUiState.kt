package com.karalo.feature.history

/** The History page's two orderings. */
enum class HistorySort(
    val label: String,
) {
    BY_DATE("By date"),
    MOST_PLAYED("Most played"),
}

/** Which right-side panel is open, if any. */
enum class HistoryPanel { NONE, SORT, CLEAR_CONFIRM }

/**
 * One entry in the History list: a non-focusable group heading (a karaoke night, or a play count)
 * or a playable song. [key] is stable and unique across the list, for LazyColumn item keys.
 */
sealed interface HistoryRow {
    val key: String

    data class Header(
        override val key: String,
        val label: String,
    ) : HistoryRow

    data class Song(
        override val key: String,
        val videoId: String,
        val title: String,
        val channelName: String,
        val thumbnailUrl: String?,
        /** Secondary line: the time it played (by date), or empty (most played). */
        val detail: String,
    ) : HistoryRow
}

data class HistoryUiState(
    val sort: HistorySort = HistorySort.BY_DATE,
    val paused: Boolean = false,
    val rows: List<HistoryRow> = emptyList(),
    /** True only for the first page of a (re)load with nothing on screen yet. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val panel: HistoryPanel = HistoryPanel.NONE,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && rows.isEmpty()
}
