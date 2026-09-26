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
import org.junit.Rule
import org.junit.Test

private const val ITEM_COUNT = 10
private const val OVERSHOOT_PRESSES = ITEM_COUNT + 5
private val ITEM_WIDTH = 200.dp

/**
 * Verifies requirement 3/8's edge behavior: holding RIGHT past the last item (or LEFT past the
 * first) must not crash and must not produce an awkward bounce or visual jump -- focus and
 * position should simply stay pinned exactly at the edge once there's nothing further to move to.
 */
@OptIn(ExperimentalTestApi::class)
class TvCarouselEdgeBoundsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun xOf(itemIndex: Int): Float =
        composeRule
            .onNodeWithText("Item $itemIndex")
            .fetchSemanticsNode()
            .boundsInRoot.left

    private fun setUpCarousel() {
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
    }

    @Test
    fun overshootingRightPastTheLastItemStaysPinnedWithNoBounce() {
        setUpCarousel()
        composeRule.onNodeWithText("Item 0").requestFocus()
        composeRule.waitForIdle()

        repeat(OVERSHOOT_PRESSES) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
        }

        val lastPos = xOf(ITEM_COUNT - 1)
        // One further press past the end should be a complete no-op: same position, no crash.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        assertEquals(
            "position should stay exactly pinned once RIGHT is held past the last item",
            lastPos.toDouble(),
            xOf(ITEM_COUNT - 1).toDouble(),
            1.0,
        )
    }

    @Test
    fun overshootingLeftPastTheFirstItemStaysPinnedWithNoBounce() {
        setUpCarousel()
        composeRule.onNodeWithText("Item 0").requestFocus()
        composeRule.waitForIdle()
        val startX = xOf(0)

        repeat(OVERSHOOT_PRESSES) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            composeRule.waitForIdle()
        }

        assertEquals(
            "position should stay exactly pinned once LEFT is held past the first item",
            startX.toDouble(),
            xOf(0).toDouble(),
            1.0,
        )
    }
}
