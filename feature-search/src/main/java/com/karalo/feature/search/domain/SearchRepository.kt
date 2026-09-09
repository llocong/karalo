package com.karalo.feature.search.domain

import com.karalo.core.common.result.AppResult

/** Takes an already-formatted query (see [KaraokeQueryFormatter]) — no rewriting happens here. */
interface SearchRepository {
    suspend fun suggestions(formattedQuery: String): AppResult<List<String>>

    suspend fun search(formattedQuery: String): AppResult<List<SearchResultItem>>
}
