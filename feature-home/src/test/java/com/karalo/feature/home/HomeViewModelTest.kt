package com.karalo.feature.home

import com.karalo.core.common.error.AppError
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.testing.MainDispatcherExtension
import com.karalo.feature.search.domain.SearchResultItem
import com.karalo.feature.search.domain.SearchYouTubeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
    fun `the default theme loads Top Picks only`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val topPicks = listOf(SearchResultItem("id1", "Song 1", "Channel", null, 200))
            coEvery { searchYouTube("karaoke") } returns AppResult.Success(topPicks)

            val viewModel = HomeViewModel(searchYouTube, sessionHolder, logger)
            viewModel.onSeasonalThemeChanged(SeasonalTheme.DEFAULT)
            advanceUntilIdle()

            assertEquals(HomeUiState(topPicks = ShelfUiState.Loaded(topPicks)), viewModel.uiState.value)
            coVerify(exactly = 1) { searchYouTube(any()) }
        }

    @Test
    fun `the Halloween theme loads Halloween Hits only, once`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val hits = listOf(SearchResultItem("id2", "Thriller", "Channel", null, 357))
            coEvery { searchYouTube("halloween karaoke") } returns AppResult.Success(hits)

            val viewModel = HomeViewModel(searchYouTube, sessionHolder, logger)
            viewModel.onSeasonalThemeChanged(SeasonalTheme.HALLOWEEN)
            viewModel.onSeasonalThemeChanged(SeasonalTheme.HALLOWEEN)
            advanceUntilIdle()

            assertEquals(ShelfUiState.Loaded(hits), viewModel.uiState.value.halloween)
            assertEquals(ShelfUiState.Loading, viewModel.uiState.value.topPicks)
            coVerify(exactly = 1) { searchYouTube("halloween karaoke") }
        }

    @Test
    fun `a shelf that fails to load surfaces an error state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            coEvery { searchYouTube("karaoke") } returns AppResult.Failure(AppError.Network())

            val viewModel = HomeViewModel(searchYouTube, sessionHolder, logger)
            viewModel.onSeasonalThemeChanged(SeasonalTheme.DEFAULT)
            advanceUntilIdle()

            assertEquals(ShelfUiState.Error, viewModel.uiState.value.topPicks)
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
