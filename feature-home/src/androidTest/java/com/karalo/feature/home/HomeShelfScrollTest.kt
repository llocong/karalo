package com.karalo.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.karalo.core.ui.theme.KaraloTheme
import com.karalo.feature.search.domain.SearchResultItem
import org.junit.Rule
import org.junit.Test

private const val ITEM_COUNT = 4

/**
 * Verifies `HomeScreenContent`'s own wiring on top of [TvCarousel][com.karalo.core.ui.components.TvCarousel]
 * -- see core-ui's `TvCarouselScrollTest` for the generic centered-scroll math every shelf relies
 * on, which this test deliberately no longer re-asserts. What's specific to this screen: all three
 * shelves render with their titles, and UP/DOWN moves focus between shelves while each shelf keeps
 * its own row of cards independently focus-managed.
 */
@OptIn(ExperimentalTestApi::class)
class HomeShelfScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shelfItems(prefix: String): List<SearchResultItem> =
        (0 until ITEM_COUNT).map { index ->
            SearchResultItem(
                videoId = "$prefix-id$index",
                title = "$prefix Item $index",
                channelName = "Channel",
                thumbnailUrl = null,
                durationSeconds = null,
            )
        }

    @Test
    fun allThreeShelvesRenderAndUpDownMovesFocusBetweenThem() {
        composeRule.setContent {
            KaraloTheme {
                HomeScreenContent(
                    uiState =
                        HomeUiState(
                            topPicks = ShelfUiState.Loaded(shelfItems("TopPicks")),
                            pop = ShelfUiState.Loaded(shelfItems("Pop")),
                            rock = ShelfUiState.Loaded(shelfItems("Rock")),
                        ),
                    onResultClick = { _, _ -> },
                    firstVideoFocusTrigger = 0,
                )
            }
        }

        composeRule.onNodeWithText("Top Picks").assertExists()
        composeRule.onNodeWithText("Pop").assertExists()
        composeRule.onNodeWithText("Rock").assertExists()

        composeRule.onNodeWithText("TopPicks Item 0").requestFocus()
        composeRule.waitForIdle()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pop Item 0").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rock Item 0").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pop Item 0").assertIsFocused()
    }
}
