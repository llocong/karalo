package com.karalo.feature.player.domain

import com.karalo.core.common.result.AppResult

interface PlaybackRepository {
    suspend fun resolveStream(videoId: String): AppResult<PlayableStream>
}
