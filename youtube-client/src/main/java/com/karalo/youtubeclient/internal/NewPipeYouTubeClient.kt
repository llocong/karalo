package com.karalo.youtubeclient.internal

import com.karalo.core.common.di.IoDispatcher
import com.karalo.core.common.error.AppError
import com.karalo.core.common.result.AppResult
import com.karalo.youtubeclient.YouTubeClient
import com.karalo.youtubeclient.model.YtStreamInfo
import com.karalo.youtubeclient.model.YtSuggestion
import com.karalo.youtubeclient.model.YtVideoSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

internal class NewPipeYouTubeClient @Inject constructor(
    // Injecting the Downloader (rather than reading it) guarantees Hilt has run
    // NewPipeBootstrap.ensureInitialized() before this client is ever used.
    @Suppress("UNUSED_PARAMETER") downloaderInitTrigger: org.schabi.newpipe.extractor.downloader.Downloader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : YouTubeClient {

    private val service = ServiceList.YouTube

    override suspend fun search(query: String): AppResult<List<YtVideoSummary>> =
        withContext(ioDispatcher) {
            runCatching {
                val queryHandler = service.searchQHFactory.fromQuery(query)
                SearchInfo.getInfo(service, queryHandler)
                    .relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map(YtMapper::toVideoSummary)
            }.toAppResult { AppError.Extraction("Search failed for \"$query\"", it) }
        }

    override suspend fun suggestions(query: String): AppResult<List<YtSuggestion>> =
        withContext(ioDispatcher) {
            runCatching {
                service.suggestionExtractor
                    ?.suggestionList(query)
                    ?.map(::YtSuggestion)
                    .orEmpty()
            }.toAppResult { AppError.Extraction("Suggestions failed for \"$query\"", it) }
        }

    override suspend fun streamsFor(videoId: String): AppResult<YtStreamInfo> =
        withContext(ioDispatcher) {
            runCatching {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val streamInfo = StreamInfo.getInfo(service, watchUrl)
                YtMapper.toStreamInfo(streamInfo)
                    ?: throw NoPlayableStreamException(videoId)
            }.toAppResult { AppError.Extraction("Stream resolution failed for $videoId", it) }
        }

    private inline fun <T> Result<T>.toAppResult(onError: (Throwable) -> AppError): AppResult<T> =
        fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(onError(it)) },
        )

    private class NoPlayableStreamException(videoId: String) :
        Exception("No playable stream found for video $videoId")
}
