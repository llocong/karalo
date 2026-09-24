package com.karalo.feature.history

import com.karalo.core.common.error.AppError
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.karaoke.domain.HistoryPlay
import com.karalo.core.karaoke.domain.KaraokeQueueSnapshot
import com.karalo.core.karaoke.domain.NowPlaying
import com.karalo.core.karaoke.domain.NowPlayingSource
import com.karalo.core.testing.FakeKaraokeRepository
import com.karalo.core.testing.MainDispatcherExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val repository = FakeKaraokeRepository()
    private val sessionHolder = mockk<SearchSessionHolder>(relaxed = true)
    private val start = Instant.parse("2026-09-24T01:00:00Z")

    private fun viewModel() =
        HistoryViewModel(repository, sessionHolder).apply {
            zone = ZoneOffset.UTC
            today = { LocalDate.of(2026, 9, 24) }
        }

    /** [count] plays, newest first, one minute apart, all in one night. */
    private fun seedPlays(
        count: Int,
        videoIdOf: (Int) -> String = { "v$it" },
    ) {
        repository.historyPlays +=
            (count - 1 downTo 0).map { i ->
                HistoryPlay(
                    "p$i",
                    videoIdOf(i),
                    "Song $i",
                    "Channel",
                    null,
                    start.plus(Duration.ofMinutes(i.toLong())),
                    start,
                )
            }
    }

    private fun HistoryUiState.songs() = rows.filterIsInstance<HistoryRow.Song>()

    @Test
    fun `nothing loads until the page is opened, then the first page appears newest first`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(120)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(0, repository.historyRequestCount)

            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(HISTORY_PAGE_SIZE, state.songs().size)
            assertEquals("Song 119", state.songs().first().title)
            assertTrue(state.rows.first() is HistoryRow.Header)
            assertFalse(state.endReached)
        }

    @Test
    fun `loadMore appends pages until the end, then stops asking`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(120)
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()

            repeat(4) {
                viewModel.loadMore()
                advanceUntilIdle()
            }

            assertEquals(
                120,
                viewModel.uiState.value
                    .songs()
                    .size,
            )
            assertTrue(viewModel.uiState.value.endReached)
            assertEquals(3, repository.historyRequestCount)
        }

    @Test
    fun `refresh keeps as many entries loaded as before, so the list doesn't shrink`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(120)
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()
            viewModel.loadMore()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(
                100,
                viewModel.uiState.value
                    .songs()
                    .size,
            )
        }

    @Test
    fun `most played aggregates repeats and heads each play count`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(6) { i -> if (i < 3) "queen" else "v$i" }
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.setSort(HistorySort.MOST_PLAYED)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(HistorySort.MOST_PLAYED, state.sort)
            assertEquals(HistoryPanel.NONE, state.panel)
            assertEquals(listOf("3 plays", "1 play"), state.rows.filterIsInstance<HistoryRow.Header>().map { it.label })
            assertEquals(4, state.songs().size)

            viewModel.setSort(HistorySort.BY_DATE)
            advanceUntilIdle()
            assertEquals(
                6,
                viewModel.uiState.value
                    .songs()
                    .size,
            )
        }

    @Test
    fun `pausing and resuming flips the state and the button, keeping existing history`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(3)
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()
            val messages = mutableListOf<String>()
            val collector = launch { viewModel.messages.collect { messages += it } }

            viewModel.togglePaused()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.paused)
            assertTrue(repository.historyPaused)
            assertEquals(
                3,
                viewModel.uiState.value
                    .songs()
                    .size,
            )

            viewModel.togglePaused()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.paused)
            assertEquals(listOf("Your song history has been paused", "Song history has been turned on."), messages)
            collector.cancel()
        }

    @Test
    fun `confirming clear empties the list, and the paused empty state is distinguishable`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(3)
            repository.historyPaused = true
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.openClearPanel()
            assertEquals(HistoryPanel.CLEAR_CONFIRM, viewModel.uiState.value.panel)
            viewModel.confirmClear()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(HistoryPanel.NONE, state.panel)
            assertTrue(state.isEmpty)
            assertTrue(state.paused)
            assertTrue(repository.historyPlays.isEmpty())
        }

    @Test
    fun `cancel leaves history untouched`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(3)
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.openClearPanel()
            viewModel.dismissPanel()
            advanceUntilIdle()

            assertEquals(HistoryPanel.NONE, viewModel.uiState.value.panel)
            assertEquals(3, repository.historyPlays.size)
        }

    @Test
    fun `a load error shows a retry, and retry recovers`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(3)
            repository.historyError = AppError.Network()
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.errorMessage)

            repository.historyError = null
            viewModel.retry()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals(
                3,
                viewModel.uiState.value
                    .songs()
                    .size,
            )
        }

    @Test
    fun `selecting a song hands it to the player without touching history`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(2)
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()

            val song =
                viewModel.uiState.value
                    .songs()
                    .first()
            viewModel.onSongSelected(song)

            verify {
                sessionHolder.setLastResults(
                    listOf(PlayableItemRef(song.videoId, song.title, "Channel", null, null)),
                )
            }
            assertEquals(2, repository.historyPlays.size)
        }

    @Test
    fun `a song finishing reloads the page so the new play shows up`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            seedPlays(2)
            val viewModel = viewModel()
            viewModel.refresh()
            advanceUntilIdle()
            repository.setQueueSnapshot(
                KaraokeQueueSnapshot(
                    "PLAYING",
                    NowPlaying(NowPlayingSource.PLAY_NOW, null, "new", "New", "C", null, null, null),
                    emptyList(),
                ),
            )
            advanceUntilIdle()

            repository.historyPlays.add(
                0,
                HistoryPlay("p-new", "new", "New", "C", null, start.plus(Duration.ofHours(1)), start),
            )
            repository.setQueueSnapshot(KaraokeQueueSnapshot.EMPTY)
            advanceUntilIdle()

            assertEquals(
                "New",
                viewModel.uiState.value
                    .songs()
                    .first()
                    .title,
            )
        }
}
