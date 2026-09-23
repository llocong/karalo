package com.karalo.feature.search.data

import com.karalo.core.common.result.AppResult
import com.karalo.core.common.result.map
import com.karalo.feature.search.domain.SearchRepository
import com.karalo.feature.search.domain.SearchResultItem
import com.karalo.youtubeclient.YouTubeClient
import com.karalo.youtubeclient.model.YtVideoSummary
import javax.inject.Inject

class SearchRepositoryImpl
    @Inject
    constructor(
        private val youTubeClient: YouTubeClient,
    ) : SearchRepository {
        override suspend fun suggestions(formattedQuery: String): AppResult<List<String>> =
            youTubeClient.suggestions(formattedQuery).map { suggestions -> suggestions.map { it.text } }

        override suspend fun search(
            formattedQuery: String,
            keep: (SearchResultItem) -> Boolean,
            minResults: Int,
        ): AppResult<List<SearchResultItem>> =
            youTubeClient
                .search(formattedQuery, keep = { keep(it.toSearchResultItem()) }, minResults = minResults)
                .map { summaries -> summaries.map { it.toSearchResultItem() } }

        private fun YtVideoSummary.toSearchResultItem() =
            SearchResultItem(
                videoId = videoId,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
            )
    }
