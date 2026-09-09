package com.karalo.feature.search.data

import com.karalo.core.common.result.AppResult
import com.karalo.core.testing.FakeYouTubeClient
import com.karalo.youtubeclient.model.YtSuggestion
import com.karalo.youtubeclient.model.YtVideoSummary
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchRepositoryImplTest {

    private val fakeClient = FakeYouTubeClient()
    private val repository = SearchRepositoryImpl(fakeClient)

    @Test
    fun `search maps YouTube client summaries onto domain results`() = runTest {
        fakeClient.searchResult = AppResult.Success(
            listOf(YtVideoSummary("abc", "Karaoke Song", "Some Channel", "https://thumb", 200L)),
        )

        val result = repository.search("karaoke rihanna")

        val expected = listOf(
            com.karalo.feature.search.domain.SearchResultItem("abc", "Karaoke Song", "Some Channel", "https://thumb", 200L),
        )
        assertEquals(AppResult.Success(expected), result)
        assertEquals("karaoke rihanna", fakeClient.lastSearchQuery)
    }

    @Test
    fun `suggestions maps YouTube client suggestions onto plain strings`() = runTest {
        fakeClient.suggestionsResult = AppResult.Success(listOf(YtSuggestion("karaoke rihanna")))

        val result = repository.suggestions("karaoke rih")

        assertEquals(AppResult.Success(listOf("karaoke rihanna")), result)
    }
}
