package com.karalo.feature.search.presentation

import com.karalo.core.common.error.AppError
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.testing.MainDispatcherExtension
import com.karalo.feature.search.domain.GetSearchSuggestionsUseCase
import com.karalo.feature.search.domain.SearchResultItem
import com.karalo.feature.search.domain.SearchYouTubeUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val getSuggestions = mockk<GetSearchSuggestionsUseCase>()
    private val searchYouTube = mockk<SearchYouTubeUseCase>()
    private val sessionHolder = mockk<SearchSessionHolder>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createViewModel() = SearchViewModel(getSuggestions, searchYouTube, sessionHolder, logger)

    @Test
    fun `submitting a query shows results and stores the queue for the player`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val items = listOf(SearchResultItem("id1", "Song", "Channel", null, 200))
            coEvery { searchYouTube("Rihanna") } returns AppResult.Success(items)
            val viewModel = createViewModel()

            viewModel.onSubmit("Rihanna")
            advanceUntilIdle()

            assertEquals(SearchUiState.Results("Rihanna", items), viewModel.uiState.value)
            verify { sessionHolder.setLastResults(match { it.single().videoId == "id1" }) }
        }

    @Test
    fun `a failed search surfaces an error state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            coEvery { searchYouTube("Adele") } returns AppResult.Failure(AppError.Network())
            val viewModel = createViewModel()

            viewModel.onSubmit("Adele")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SearchUiState.Error)
        }

    @Test
    fun `typing debounces before requesting suggestions`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            coEvery { getSuggestions("Rih") } returns AppResult.Success(listOf("karaoke rihanna"))
            val viewModel = createViewModel()

            viewModel.onQueryChanged("Rih")
            advanceUntilIdle()

            assertEquals(SearchUiState.Suggesting("Rih", listOf("karaoke rihanna")), viewModel.uiState.value)
        }

    @Test
    fun `submitting does not get clobbered by a suggestions fetch that was already in flight`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            // Reproduces a real bug: typing starts the debounced suggestions fetch; submitting
            // immediately after (before that debounce fires) must not let the stale suggestions
            // result overwrite the Results state once it lands.
            val items = listOf(SearchResultItem("id1", "Song", "Channel", null, 200))
            coEvery { getSuggestions("Test Song") } returns AppResult.Success(listOf("karaoke test song"))
            coEvery { searchYouTube("Test Song") } returns AppResult.Success(items)
            val viewModel = createViewModel()

            viewModel.onQueryChanged("Test Song")
            viewModel.onSubmit("Test Song")
            advanceUntilIdle()

            assertEquals(SearchUiState.Results("Test Song", items), viewModel.uiState.value)
        }

    @Test
    fun `suggestions resume once the user edits the text after submitting`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            coEvery { searchYouTube("Test Song") } returns AppResult.Success(emptyList())
            coEvery { getSuggestions("Test Song 2") } returns AppResult.Success(listOf("karaoke test song 2"))
            val viewModel = createViewModel()
            viewModel.onSubmit("Test Song")
            advanceUntilIdle()

            viewModel.onQueryChanged("Test Song 2")
            advanceUntilIdle()

            assertEquals(
                SearchUiState.Suggesting("Test Song 2", listOf("karaoke test song 2")),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `clearing the query resets state to idle`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.onQueryChanged("")

            assertEquals(SearchUiState.Idle, viewModel.uiState.value)
        }
}
