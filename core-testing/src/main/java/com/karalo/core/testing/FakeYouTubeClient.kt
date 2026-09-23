package com.karalo.core.testing

import com.karalo.core.common.error.AppError
import com.karalo.core.common.result.AppResult
import com.karalo.youtubeclient.YouTubeClient
import com.karalo.youtubeclient.model.YtStreamInfo
import com.karalo.youtubeclient.model.YtSuggestion
import com.karalo.youtubeclient.model.YtVideoSummary

/** Scriptable [YouTubeClient] test double shared by every module that talks to it. */
class FakeYouTubeClient : YouTubeClient {
    var searchResult: AppResult<List<YtVideoSummary>> = AppResult.Success(emptyList())
    var suggestionsResult: AppResult<List<YtSuggestion>> = AppResult.Success(emptyList())
    var streamResult: AppResult<YtStreamInfo> = AppResult.Failure(AppError.NotFound)

    var lastSearchQuery: String? = null
    var lastSuggestionsQuery: String? = null
    var lastStreamVideoId: String? = null

    override suspend fun search(
        query: String,
        keep: (YtVideoSummary) -> Boolean,
        minResults: Int,
    ): AppResult<List<YtVideoSummary>> {
        lastSearchQuery = query
        return when (val result = searchResult) {
            is AppResult.Success -> AppResult.Success(result.data.filter(keep))
            is AppResult.Failure -> result
        }
    }

    override suspend fun suggestions(query: String): AppResult<List<YtSuggestion>> {
        lastSuggestionsQuery = query
        return suggestionsResult
    }

    override suspend fun streamsFor(videoId: String): AppResult<YtStreamInfo> {
        lastStreamVideoId = videoId
        return streamResult
    }
}
