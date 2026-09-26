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
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.ui.theme.KaraloTheme
import com.karalo.feature.search.domain.SearchResultItem
import org.junit.Rule
import org.junit.Test

private const val ITEM_COUNT = 4

/**
 * Verifies `HomeScreenContent`'s own wiring on top of [TvCarousel][com.karalo.core.ui.components.TvCarousel]
 * -- see core-ui's `TvCarouselScrollTest` for the generic centered-scroll math every shelf relies
 * on, which this test deliberately doesn't re-assert. What's specific to this screen: it shows one
 * shelf, picked by the seasonal theme -- Top Picks by default, Halloween Hits under Halloween.
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

    private fun setContent(theme: SeasonalTheme) {
        composeRule.setContent {
            KaraloTheme(seasonalTheme = theme) {
                HomeScreenContent(
                    uiState =
                        HomeUiState(
                            topPicks = ShelfUiState.Loaded(shelfItems("TopPicks")),
                            halloween = ShelfUiState.Loaded(shelfItems("Halloween")),
                        ),
                    onResultClick = { _, _ -> },
                    firstVideoFocusTrigger = 0,
                )
            }
        }
    }

    @Test
    fun defaultThemeShowsOnlyTopPicksAndRightBrowsesIt() {
        setContent(SeasonalTheme.DEFAULT)

        composeRule.onNodeWithText("Top Picks").assertExists()
        composeRule.onNodeWithText("Halloween Hits").assertDoesNotExist()
        composeRule.onNodeWithText("Halloween Item 0").assertDoesNotExist()

        composeRule.onNodeWithText("TopPicks Item 0").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("TopPicks Item 1").assertIsFocused()
    }

    @Test
    fun halloweenThemeShowsOnlyHalloweenHits() {
        setContent(SeasonalTheme.HALLOWEEN)

        composeRule.onNodeWithText("Halloween Hits").assertExists()
        composeRule.onNodeWithText("Halloween Item 0").assertExists()
        composeRule.onNodeWithText("Top Picks").assertDoesNotExist()
        composeRule.onNodeWithText("TopPicks Item 0").assertDoesNotExist()
    }
}
