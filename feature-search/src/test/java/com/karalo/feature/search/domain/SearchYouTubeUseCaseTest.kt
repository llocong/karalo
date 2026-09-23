package com.karalo.feature.search.domain

import com.karalo.core.common.error.AppError
import com.karalo.core.common.result.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchYouTubeUseCaseTest {
    private val repository = mockk<SearchRepository>()
    private val useCase = SearchYouTubeUseCase(repository)

    @Test
    fun `applies the karaoke prefix before calling the repository`() =
        runTest {
            val items = listOf(SearchResultItem("id1", "Song", "Channel", null, 200))
            coEvery { repository.search("karaoke Rihanna", any(), any()) } returns AppResult.Success(items)

            val result = useCase("Rihanna")

            assertEquals(AppResult.Success(items), result)
            coVerify { repository.search("karaoke Rihanna", any(), any()) }
        }

    @Test
    fun `asks the repository to keep only karaoke results, with a one-page top-up threshold`() =
        runTest {
            val keep = slot<(SearchResultItem) -> Boolean>()
            val minResults = slot<Int>()
            coEvery {
                repository.search(any(), capture(keep), capture(minResults))
            } returns AppResult.Success(emptyList())

            useCase("machine")

            assertTrue(keep.captured(SearchResultItem("a", "Machine (Karaoke Version)", "Channel", null, 200)))
            assertFalse(keep.captured(SearchResultItem("b", "Best Karaoke Machine Review", "Channel", null, 200)))
            assertEquals(KaraokeResultFilter.MIN_RESULTS_BEFORE_TOP_UP, minResults.captured)
        }

    @Test
    fun `returns an empty success without calling the repository for a blank query`() =
        runTest {
            val result = useCase("   ")

            assertEquals(AppResult.Success(emptyList<SearchResultItem>()), result)
            coVerify(exactly = 0) { repository.search(any(), any(), any()) }
        }

    @Test
    fun `propagates repository failures`() =
        runTest {
            val error = AppError.Network()
            coEvery { repository.search(any(), any(), any()) } returns AppResult.Failure(error)

            val result = useCase("Adele")

            assertEquals(AppResult.Failure(error), result)
        }
}
