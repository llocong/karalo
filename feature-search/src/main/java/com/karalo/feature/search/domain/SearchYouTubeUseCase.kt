package com.karalo.feature.search.domain

import com.karalo.core.common.result.AppResult
import javax.inject.Inject

class SearchYouTubeUseCase
    @Inject
    constructor(
        private val repository: SearchRepository,
    ) {
        suspend operator fun invoke(rawQuery: String): AppResult<List<SearchResultItem>> {
            val trimmed = rawQuery.trim()
            if (trimmed.isEmpty()) return AppResult.Success(emptyList())
            return repository.search(KaraokeQueryFormatter.format(trimmed))
        }
    }
