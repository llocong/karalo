package com.karalo.feature.search.domain

import com.karalo.core.common.error.AppError
import com.karalo.core.common.result.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchYouTubeUseCaseTest {
    private val repository = mockk<SearchRepository>()
    private val useCase = SearchYouTubeUseCase(repository)

    @Test
    fun `applies the karaoke prefix before calling the repository`() =
        runTest {
            val items = listOf(SearchResultItem("id1", "Song", "Channel", null, 200))
            coEvery { repository.search("karaoke Rihanna") } returns AppResult.Success(items)

            val result = useCase("Rihanna")

            assertEquals(AppResult.Success(items), result)
            coVerify { repository.search("karaoke Rihanna") }
        }

    @Test
    fun `returns an empty success without calling the repository for a blank query`() =
        runTest {
            val result = useCase("   ")

            assertEquals(AppResult.Success(emptyList<SearchResultItem>()), result)
            coVerify(exactly = 0) { repository.search(any()) }
        }

    @Test
    fun `propagates repository failures`() =
        runTest {
            val error = AppError.Network()
            coEvery { repository.search(any()) } returns AppResult.Failure(error)

            val result = useCase("Adele")

            assertEquals(AppResult.Failure(error), result)
        }
}
