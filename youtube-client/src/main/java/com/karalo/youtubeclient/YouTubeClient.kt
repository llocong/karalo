package com.karalo.youtubeclient

import com.karalo.core.common.result.AppResult
import com.karalo.youtubeclient.model.YtStreamInfo
import com.karalo.youtubeclient.model.YtSuggestion
import com.karalo.youtubeclient.model.YtVideoSummary

/**
 * The app's only window into YouTube. The implementation is unofficial extraction
 * (NewPipeExtractor) rather than YouTube's Data API, so it can be swapped later without any
 * caller (`:feature-search`, `:feature-player`) changing — see docs/adr/0002.
 *
 * Callers pass the query/videoId as-is; any query rewriting (e.g. the "karaoke " prefix) is a
 * feature-layer concern, not this client's.
 */
interface YouTubeClient {
    /**
     * Returns the first page of results that pass [keep]. If fewer than [minResults] pass, one
     * more page is fetched and filtered too (never more than one).
     */
    suspend fun search(
        query: String,
        keep: (YtVideoSummary) -> Boolean = { true },
        minResults: Int = 0,
    ): AppResult<List<YtVideoSummary>>

    suspend fun suggestions(query: String): AppResult<List<YtSuggestion>>

    suspend fun streamsFor(videoId: String): AppResult<YtStreamInfo>
}
