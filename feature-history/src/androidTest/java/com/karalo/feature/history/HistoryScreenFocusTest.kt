package com.karalo.feature.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.karalo.core.ui.theme.KaraloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val SONG_COUNT = 300

@OptIn(ExperimentalTestApi::class)
class HistoryScreenFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val rows: List<HistoryRow> =
        listOf(HistoryRow.Header("night-1", "Wednesday, September 23")) +
            (0 until SONG_COUNT).map { i -> HistoryRow.Song("play-$i", "vid$i", "Song $i", "Channel", null, "9:00 PM") }

    private var state by mutableStateOf(HistoryUiState(rows = rows, isLoading = false, endReached = true))
    private var loadMoreCalls = 0

    private fun setContent() {
        composeRule.setContent {
            KaraloTheme {
                HistoryScreenContent(
                    uiState = state,
                    onSongClick = {},
                    onLoadMore = { loadMoreCalls++ },
                    onRetry = {},
                    onSortClick = { state = state.copy(panel = HistoryPanel.SORT) },
                    onPauseClick = {},
                    onClearClick = { state = state.copy(panel = HistoryPanel.CLEAR_CONFIRM) },
                    onSortSelected = { state = state.copy(sort = it, panel = HistoryPanel.NONE) },
                    onConfirmClear = {},
                    onDismissPanel = { state = state.copy(panel = HistoryPanel.NONE) },
                    contentFocusTrigger = 1,
                )
            }
        }
    }

    @Test
    fun rapidDownPressesKeepTheFocusedRowOnScreen() {
        setContent()
        composeRule.onNodeWithText("Song 0").assertIsFocused()

        repeat(40) { composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Song 40").assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun clearPanelOpensWithCancelFocusedAndBackClosesIt() {
        setContent()
        composeRule.onNodeWithText("Clear history").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HISTORY_TAG_CANCEL).assertIsFocused()
        composeRule.onNodeWithText("This will permanently remove all songs from your history.").assertIsDisplayed()

        composeRule.onRoot().performKeyInput { pressKey(Key.Back) }
        composeRule.waitForIdle()

        assertEquals(HistoryPanel.NONE, state.panel)
        composeRule.onNodeWithText("Clear history").assertIsFocused()
    }

    @Test
    fun sortPanelFocusesTheCurrentOptionAndSelectingClosesIt() {
        setContent()
        composeRule.onNodeWithText("Sort: By date").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("By date") and isFocused()).assertExists()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertEquals(HistorySort.MOST_PLAYED, state.sort)
        assertEquals(HistoryPanel.NONE, state.panel)
    }

    @Test
    fun pausedEmptyStateExplainsHowToResume() {
        state = HistoryUiState(rows = emptyList(), isLoading = false, paused = true, endReached = true)
        setContent()

        composeRule.onNodeWithText("Song history is paused").assertIsDisplayed()
        composeRule.onNodeWithText("Resume song history").assertIsDisplayed()
        composeRule.onNodeWithTag(HISTORY_TAG_PAUSE).assertIsFocused()
    }
}
