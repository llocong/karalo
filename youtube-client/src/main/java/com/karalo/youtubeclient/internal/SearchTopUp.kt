package com.karalo.youtubeclient.internal

/**
 * Filters [firstPage] with [keep] and, only if fewer than [minResults] survive, fetches and
 * filters exactly one more page. Never goes further: if results are still few after that, the
 * query just doesn't have many matches. [fetchNextPage] returns null when there's no next page;
 * if it throws, the first page's results are returned rather than failing the whole search.
 */
internal fun <T> filterWithOneTopUp(
    firstPage: List<T>,
    keep: (T) -> Boolean,
    minResults: Int,
    fetchNextPage: () -> List<T>?,
): List<T> {
    val kept = firstPage.filter(keep)
    if (kept.size >= minResults) return kept
    val nextPage = runCatching(fetchNextPage).getOrNull() ?: return kept
    return kept + nextPage.filter(keep)
}
