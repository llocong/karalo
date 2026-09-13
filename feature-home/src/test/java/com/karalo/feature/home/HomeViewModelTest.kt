package com.karalo.feature.home

import com.karalo.core.common.error.AppError
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.testing.MainDispatcherExtension
import com.karalo.feature.search.domain.SearchResultItem
import com.karalo.feature.search.domain.SearchYouTubeUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val searchYouTube = mockk<SearchYouTubeUseCase>()
    private val sessionHolder = mockk<SearchSessionHolder>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createViewModel(): HomeViewModel {
        coEvery { searchYouTube(any()) } returns AppResult.Success(emptyList())
        return HomeViewModel(searchYouTube, sessionHolder, logger)
    }

    @Test
    fun `loads all three shelves on init`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val topPicks = listOf(SearchResultItem("id1", "Song 1", "Channel", null, 200))
            val pop = listOf(SearchResultItem("id2", "Song 2", "Channel", null, 180))
            val rock = listOf(SearchResultItem("id3", "Song 3", "Channel", null, 220))
            coEvery { searchYouTube("karaoke") } returns AppResult.Success(topPicks)
            coEvery { searchYouTube("karaoke pop") } returns AppResult.Success(pop)
            coEvery { searchYouTube("karaoke rock") } returns AppResult.Success(rock)

            val viewModel = HomeViewModel(searchYouTube, sessionHolder, logger)
            advanceUntilIdle()

            val expected =
                HomeUiState(
                    topPicks = ShelfUiState.Loaded(topPicks),
                    pop = ShelfUiState.Loaded(pop),
                    rock = ShelfUiState.Loaded(rock),
                )
            assertEquals(expected, viewModel.uiState.value)
        }

    @Test
    fun `a shelf that fails to load surfaces an error state without affecting the others`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val pop = listOf(SearchResultItem("id2", "Song 2", "Channel", null, 180))
            coEvery { searchYouTube("karaoke") } returns AppResult.Failure(AppError.Network())
            coEvery { searchYouTube("karaoke pop") } returns AppResult.Success(pop)
            coEvery { searchYouTube("karaoke rock") } returns AppResult.Success(emptyList())

            val viewModel = HomeViewModel(searchYouTube, sessionHolder, logger)
            advanceUntilIdle()

            assertEquals(ShelfUiState.Error, viewModel.uiState.value.topPicks)
            assertEquals(ShelfUiState.Loaded(pop), viewModel.uiState.value.pop)
        }

    @Test
    fun `clicking a result stores its shelf as the player queue`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val items = listOf(SearchResultItem("id1", "Song", "Channel", null, 200))

            viewModel.onResultClicked(items)

            verify { sessionHolder.setLastResults(match { it.single().videoId == "id1" }) }
        }
}
