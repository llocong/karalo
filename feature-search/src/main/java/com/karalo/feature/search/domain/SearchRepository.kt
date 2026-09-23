package com.karalo.feature.search.domain

import com.karalo.core.common.result.AppResult

/** Takes an already-formatted query (see [KaraokeQueryFormatter]) — no rewriting happens here. */
interface SearchRepository {
    suspend fun suggestions(formattedQuery: String): AppResult<List<String>>

    /** Only results passing [keep] are returned; see [com.karalo.youtubeclient.YouTubeClient.search]. */
    suspend fun search(
        formattedQuery: String,
        keep: (SearchResultItem) -> Boolean = { true },
        minResults: Int = 0,
    ): AppResult<List<SearchResultItem>>
}
