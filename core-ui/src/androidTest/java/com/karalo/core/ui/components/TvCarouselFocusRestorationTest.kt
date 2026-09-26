package com.karalo.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import org.junit.Rule
import org.junit.Test

private const val ITEM_COUNT = 8
private val ITEM_WIDTH = 200.dp

/**
 * Verifies [TvCarousel]'s [androidx.compose.ui.focus.focusRestorer] usage: focusing an item, then
 * moving focus to an external sibling (simulating arrowing UP to another row, or a rail focus-
 * preview round trip) and back into the row via a real arrow-key traversal (not a direct
 * `requestFocus()`, which would bypass the restore mechanism entirely -- see [TvCarousel]'s own
 * doc), should restore focus to that same item rather than defaulting back to the first one.
 */
@OptIn(ExperimentalTestApi::class)
class TvCarouselFocusRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reenteringTheRowRestoresThePreviouslyFocusedItem() {
        val externalFocusRequester = FocusRequester()

        composeRule.setKeyboardModeContent {
            KaraloTheme {
                Column {
                    Text(
                        text = "External",
                        modifier =
                            Modifier
                                .focusRequester(externalFocusRequester)
                                .clickable {},
                    )
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

        composeRule.onNodeWithText("Item 3").requestFocus()
        composeRule.waitForIdle()

        // Arrow away, exactly the way a user would (UP out of the row), not a direct
        // requestFocus() -- a direct request wouldn't exercise focusRestorer's onExit/onEnter path
        // at all.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("External").assertIsFocused()

        // Arrow back down into the row -- a real focus-search traversal crossing into the group,
        // which is what triggers focusRestorer's restore.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 3").assertIsFocused()
    }
}
