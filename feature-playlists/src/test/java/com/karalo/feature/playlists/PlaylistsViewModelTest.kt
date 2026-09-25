package com.karalo.feature.playlists

import com.karalo.core.common.error.AppError
import com.karalo.core.common.logging.Logger
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
class PlaylistsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val searchYouTube = mockk<SearchYouTubeUseCase>()
    private val sessionHolder = mockk<SearchSessionHolder>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun song(id: String) = SearchResultItem(id, "Song $id", "Channel", null, 200)

    private fun createViewModel(): PlaylistsViewModel {
        coEvery { searchYouTube(any()) } answers { AppResult.Success(listOf(song(firstArg()))) }
        return PlaylistsViewModel(searchYouTube, sessionHolder, logger)
    }

    @Test
    fun `playlists are listed in the design's order`() {
        assertEquals(
            listOf(
                "Top Picks",
                "Duets",
                "Pop",
                "Rock",
                "R&B",
                "Latin",
                "Hip-Hop",
                "French Variety",
                "Disney",
                "90’s Hits",
                "80’s Hits",
                "70’s Hits",
                "60’s Hits",
                "Vietnamese",
            ),
            PLAYLISTS.map { it.name },
        )
    }

    @Test
    fun `opens on Top Picks and loads it plus the next playlist`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0, state.selectedIndex)
            assertEquals(PlaylistSongsState.Loaded(listOf(song("karaoke"))), state.selectedSongs)
            coVerify(exactly = 1) { searchYouTube("karaoke") }
            coVerify(exactly = 1) { searchYouTube("karaoke duets") }
            coVerify(exactly = 2) { searchYouTube(any()) }
        }

    @Test
    fun `focusing a playlist selects it and loads it and its neighbours once`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onPlaylistFocused(3)
            assertEquals("Rock", viewModel.uiState.value.selected.name)
            viewModel.onPlaylistFocused(2)
            viewModel.onPlaylistFocused(3)
            advanceUntilIdle()

            assertEquals(PlaylistSongsState.Loaded(listOf(song("karaoke rock"))), viewModel.uiState.value.selectedSongs)
            coVerify(exactly = 1) { searchYouTube("karaoke pop") }
            coVerify(exactly = 1) { searchYouTube("karaoke rock") }
            coVerify(exactly = 1) { searchYouTube("karaoke r&b") }
        }

    @Test
    fun `a playlist that fails shows an error and is retried when focused again`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel()
            coEvery { searchYouTube("karaoke vietnamese") } returns AppResult.Failure(AppError.Network())
            viewModel.onPlaylistFocused(13)
            advanceUntilIdle()
            assertEquals(PlaylistSongsState.Error, viewModel.uiState.value.selectedSongs)

            coEvery { searchYouTube("karaoke vietnamese") } returns AppResult.Success(listOf(song("v")))
            viewModel.onPlaylistFocused(13)
            advanceUntilIdle()

            assertEquals(PlaylistSongsState.Loaded(listOf(song("v"))), viewModel.uiState.value.selectedSongs)
        }

    @Test
    fun `clicking a song stores its playlist as the player queue`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onResultClicked(listOf(song("id1")))

            verify { sessionHolder.setLastResults(match { it.single().videoId == "id1" }) }
        }
}
