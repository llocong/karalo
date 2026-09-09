package com.karalo.feature.search.domain

import com.karalo.core.common.result.AppResult
import javax.inject.Inject

class GetSearchSuggestionsUseCase @Inject constructor(
    private val repository: SearchRepository,
) {
    suspend operator fun invoke(rawQuery: String): AppResult<List<String>> {
        if (rawQuery.isBlank()) return AppResult.Success(emptyList())
        return repository.suggestions(KaraokeQueryFormatter.format(rawQuery))
    }
}
