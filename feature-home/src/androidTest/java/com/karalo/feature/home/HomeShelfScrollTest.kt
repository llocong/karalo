package com.karalo.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.karalo.core.ui.theme.KaraloTheme
import com.karalo.feature.search.domain.SearchResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ITEM_COUNT = 8
private const val POSITION_TOLERANCE_PX = 1f

/**
 * Verifies the Home shelf's YouTube-on-Google-TV-style carousel scrolling: the first two cards
 * stay pinned at the row's start, the focused card is kept centered through the middle of the
 * row, and the last two cards stay pinned at the row's end -- symmetric in both directions. See
 * [CenteredBringIntoViewSpec] in `HomeScreen.kt`.
 */
@OptIn(ExperimentalTestApi::class)
class HomeShelfScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val items =
        (0 until ITEM_COUNT).map { index ->
            SearchResultItem(
                videoId = "id$index",
                title = "Item $index",
                channelName = "Channel",
                thumbnailUrl = null,
                durationSeconds = null,
            )
        }

    private fun xOf(itemIndex: Int): Float =
        composeRule
            .onNodeWithText("Item $itemIndex")
            .fetchSemanticsNode()
            .boundsInRoot.left

    private fun assertPositionEquals(
        expected: Float,
        actual: Float,
        message: String,
    ) {
        assertEquals(message, expected.toDouble(), actual.toDouble(), POSITION_TOLERANCE_PX.toDouble())
    }

    private fun pressRight() {
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
    }

    private fun pressLeft() {
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
    }

    @Test
    fun carouselKeepsFirstAndLastTwoCardsPinnedAndCentersInBetween() {
        composeRule.setContent {
            KaraloTheme {
                HomeScreenContent(
                    uiState = HomeUiState(topPicks = ShelfUiState.Loaded(items)),
                    onResultClick = { _, _ -> },
                    firstVideoFocusTrigger = 0,
                )
            }
        }

        composeRule.onNodeWithText("Item 0").requestFocus()
        composeRule.waitForIdle()
        val startX = xOf(0)

        // Card 1 -> 2: no scroll.
        pressRight()
        assertPositionEquals(startX, xOf(0), "no scroll expected moving from card 1 to card 2")

        // Card 2 -> 3: scrolling starts -- item 1 (still visible, adjacent to the newly-focused
        // item 2) shifts away from its pinned-at-start position.
        val item1AtStart = xOf(1)
        pressRight()
        assertTrue(
            "expected scrolling to start once the 3rd card is focused",
            xOf(1) < item1AtStart - POSITION_TOLERANCE_PX,
        )

        // Middle of the row: every newly-focused card should land at the same centered position
        // as the 3rd card did.
        val centeredX = xOf(2)
        for (index in 3 until ITEM_COUNT - 2) {
            pressRight()
            assertPositionEquals(centeredX, xOf(index), "card $index should be centered the same as earlier cards")
        }

        // Reach the 2nd-to-last card, then the last one: no further scroll between them -- checked
        // via the 2nd-to-last card's own position, which stays visible right next to the last one.
        pressRight() // now on index ITEM_COUNT - 2
        val secondToLastPos = xOf(ITEM_COUNT - 2)
        pressRight() // now on index ITEM_COUNT - 1 (last)
        assertPositionEquals(
            secondToLastPos,
            xOf(ITEM_COUNT - 2),
            "no scroll expected moving from the 2nd-to-last card to the last card",
        )

        // Reverse: last -> 2nd-to-last, no scroll.
        pressLeft()
        assertPositionEquals(
            secondToLastPos,
            xOf(ITEM_COUNT - 2),
            "no scroll expected moving back from the last card to the 2nd-to-last card",
        )

        // Back through the middle -- mirrors the forward pass exactly.
        for (index in ITEM_COUNT - 3 downTo 3) {
            pressLeft()
            assertPositionEquals(centeredX, xOf(index), "card $index should be centered the same as on the way in")
        }

        // Card 3 -> 2: still centered.
        pressLeft()
        assertPositionEquals(centeredX, xOf(2), "card 2 should still be centered")

        // Card 2 -> 1: scrolling stops, the row starts returning to its start.
        val item1BeforeFinalStep = xOf(1)
        pressLeft()
        assertTrue(
            "expected the row to scroll back toward its start once card 2 is focused",
            xOf(1) > item1BeforeFinalStep + POSITION_TOLERANCE_PX,
        )

        // Card 1 -> 0: no scroll, and the row is back exactly at its original start position.
        pressLeft()
        assertPositionEquals(startX, xOf(0), "row should return exactly to its original start position")
    }
}
