package com.karalo.feature.player.domain

import com.karalo.core.common.result.AppResult
import javax.inject.Inject

class ResolveStreamUseCase
    @Inject
    constructor(
        private val repository: PlaybackRepository,
    ) {
        suspend operator fun invoke(videoId: String): AppResult<PlayableStream> = repository.resolveStream(videoId)
    }
