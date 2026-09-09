package com.karalo.feature.search.domain

import com.karalo.core.common.result.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetSearchSuggestionsUseCaseTest {

    private val repository = mockk<SearchRepository>()
    private val useCase = GetSearchSuggestionsUseCase(repository)

    @Test
    fun `applies the karaoke prefix before requesting suggestions`() = runTest {
        coEvery { repository.suggestions("karaoke rih") } returns AppResult.Success(listOf("karaoke rihanna"))

        val result = useCase("rih")

        assertEquals(AppResult.Success(listOf("karaoke rihanna")), result)
        coVerify { repository.suggestions("karaoke rih") }
    }

    @Test
    fun `returns an empty success without calling the repository for a blank query`() = runTest {
        val result = useCase("")

        assertEquals(AppResult.Success(emptyList<String>()), result)
        coVerify(exactly = 0) { repository.suggestions(any()) }
    }
}
