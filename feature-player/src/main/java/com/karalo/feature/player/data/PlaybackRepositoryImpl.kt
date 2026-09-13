package com.karalo.feature.player.data

import com.karalo.core.common.result.AppResult
import com.karalo.core.common.result.map
import com.karalo.feature.player.domain.PlayableStream
import com.karalo.feature.player.domain.PlaybackRepository
import com.karalo.youtubeclient.YouTubeClient
import javax.inject.Inject

class PlaybackRepositoryImpl
    @Inject
    constructor(
        private val youTubeClient: YouTubeClient,
    ) : PlaybackRepository {
        override suspend fun resolveStream(videoId: String): AppResult<PlayableStream> =
            youTubeClient.streamsFor(videoId).map { stream ->
                PlayableStream(uri = stream.playbackUrl, mimeType = stream.mimeType, isAdaptive = stream.isAdaptive)
            }
    }
