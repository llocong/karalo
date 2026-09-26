package com.karalo.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.karalo.core.ui.testing.setKeyboardModeContent
import com.karalo.core.ui.theme.KaraloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ITEM_COUNT = 8
private const val POSITION_TOLERANCE_PX = 1f
private val ITEM_WIDTH = 200.dp

/**
 * Verifies [TvCarousel]'s YouTube-on-Google-TV-style carousel scrolling directly, independent of
 * any specific caller: the first two items stay pinned at the row's start, the focused item is
 * kept centered through the middle of the row, and the last two items stay pinned at the row's
 * end -- symmetric in both directions. See [CenteredBringIntoViewSpec]. This supersedes the
 * equivalent, now-trimmed assertions in `feature-home`'s `HomeShelfScrollTest`, which only checks
 * `HomeScreenContent`'s own wiring on top of this shared engine.
 */
@OptIn(ExperimentalTestApi::class)
class TvCarouselScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

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
    fun carouselKeepsFirstAndLastTwoItemsPinnedAndCentersInBetween() {
        composeRule.setKeyboardModeContent {
            KaraloTheme {
                TvCarousel(
                    items = (0 until ITEM_COUNT).map { "Item $it" },
                    key = { it },
                ) { _, item, itemModifier ->
                    Text(
                        text = item,
                        modifier = Modifier.width(ITEM_WIDTH).then(itemModifier).clickable {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Item 0").requestFocus()
        composeRule.waitForIdle()
        val startX = xOf(0)

        // Item 1 -> 2: no scroll.
        pressRight()
        assertPositionEquals(startX, xOf(0), "no scroll expected moving from item 1 to item 2")

        // Item 2 -> 3: scrolling starts -- item 1 (still visible, adjacent to the newly-focused
        // item 2) shifts away from its pinned-at-start position.
        val item1AtStart = xOf(1)
        pressRight()
        assertTrue(
            "expected scrolling to start once the 3rd item is focused",
            xOf(1) < item1AtStart - POSITION_TOLERANCE_PX,
        )

        // Middle of the row: every newly-focused item should land at the same centered position
        // as the 3rd item did.
        val centeredX = xOf(2)
        for (index in 3 until ITEM_COUNT - 2) {
            pressRight()
            assertPositionEquals(centeredX, xOf(index), "item $index should be centered the same as earlier items")
        }

        // Reach the 2nd-to-last item, then the last one: no further scroll between them -- checked
        // via the 2nd-to-last item's own position, which stays visible right next to the last one.
        pressRight() // now on index ITEM_COUNT - 2
        val secondToLastPos = xOf(ITEM_COUNT - 2)
        pressRight() // now on index ITEM_COUNT - 1 (last)
        assertPositionEquals(
            secondToLastPos,
            xOf(ITEM_COUNT - 2),
            "no scroll expected moving from the 2nd-to-last item to the last item",
        )

        // Reverse: last -> 2nd-to-last, no scroll.
        pressLeft()
        assertPositionEquals(
            secondToLastPos,
            xOf(ITEM_COUNT - 2),
            "no scroll expected moving back from the last item to the 2nd-to-last item",
        )

        // Back through the middle -- mirrors the forward pass exactly.
        for (index in ITEM_COUNT - 3 downTo 3) {
            pressLeft()
            assertPositionEquals(centeredX, xOf(index), "item $index should be centered the same as on the way in")
        }

        // Item 3 -> 2: still centered.
        pressLeft()
        assertPositionEquals(centeredX, xOf(2), "item 2 should still be centered")

        // Item 2 -> 1: scrolling stops, the row starts returning to its start.
        val item1BeforeFinalStep = xOf(1)
        pressLeft()
        assertTrue(
            "expected the row to scroll back toward its start once item 2 is focused",
            xOf(1) > item1BeforeFinalStep + POSITION_TOLERANCE_PX,
        )

        // Item 1 -> 0: no scroll, and the row is back exactly at its original start position.
        pressLeft()
        assertPositionEquals(startX, xOf(0), "row should return exactly to its original start position")
    }
}
