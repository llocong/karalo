package com.karalo.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
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

private const val ITEM_COUNT = 20
private const val SCROLL_DEPTH = 12
private val ITEM_WIDTH = 200.dp

/**
 * Verifies [TvCarousel]'s explicit "jump to the first item" mechanism (`focusFirstItemTrigger`) --
 * e.g. an explicit rail reselect while the row is already showing, scrolled well past its start.
 * Also exercises the start-edge half of requirement 3/8's "no bounce" behavior, since jumping back
 * to item 0 from deep in the row must land exactly at the row's original start position.
 */
@OptIn(ExperimentalTestApi::class)
class TvCarouselFirstItemJumpTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var bumpTrigger: () -> Unit

    private fun xOf(itemIndex: Int): Float =
        composeRule
            .onNodeWithText("Item $itemIndex")
            .fetchSemanticsNode()
            .boundsInRoot.left

    @Test
    fun explicitTriggerSnapsFocusAndPositionBackToTheFirstItem() {
        val firstItemFocusRequester = FocusRequester()

        composeRule.setKeyboardModeContent {
            var trigger by remember { mutableIntStateOf(0) }
            bumpTrigger = { trigger++ }
            KaraloTheme {
                TvCarousel(
                    items = (0 until ITEM_COUNT).map { "Item $it" },
                    key = { it },
                    firstItemFocusRequester = firstItemFocusRequester,
                    focusFirstItemTrigger = trigger,
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

        repeat(SCROLL_DEPTH) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
        }
        // Confirm we've actually scrolled away before jumping back -- otherwise this test wouldn't
        // be exercising anything. Checked via the now-focused item's own identity rather than item
        // 0's position: this row's cache window (see TvCarouselDefaults) only keeps items within a
        // bounded distance of the visible window composed, so by this point item 0 -- this far
        // behind -- is no longer composed at all, and querying it here would fail outright rather
        // than just returning a stale position.
        composeRule.onNodeWithText("Item $SCROLL_DEPTH").assertIsFocused()

        composeRule.runOnIdle { bumpTrigger() }
        composeRule.waitForIdle()

        assertEquals(
            "row should be back at its original start position after the jump-to-first trigger",
            startX.toDouble(),
            xOf(0).toDouble(),
            1.0,
        )
    }
}
