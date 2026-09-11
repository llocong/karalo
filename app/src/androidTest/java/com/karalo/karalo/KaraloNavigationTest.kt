package com.karalo.karalo

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import com.karalo.feature.search.presentation.SEARCH_QUERY_FIELD_TAG
import com.karalo.karalo.nav.NAV_TAG_SEARCH
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val MAX_BACK_PRESS_ATTEMPTS = 4

/**
 * Exercises the full v1 nav flow end to end (Home -> Search -> Results -> Player -> Back) against
 * a scripted [com.karalo.core.testing.FakeYouTubeClient] (see FakeYouTubeClientModule), so it
 * covers real D-pad-navigable UI wiring without hitting the network or real YouTube.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class KaraloNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun searchingAndSelectingAResultOpensThePlayer() {
        // Which nav item (if any) ends up with real initial keyboard focus is environment-
        // dependent (observed to differ between a manual run and this test harness, and Home's
        // own composition got heavier with the new shelves) -- rather than assume it lands on
        // Search, request focus on it directly via its semantics node. TV Material3's
        // NavigationDrawerItem, like Card, only wires its onClick to real key/remote input, not
        // Compose test's semantics-based performClick() -- and performKeyInput dispatches to
        // whichever node currently holds focus, not to the node it's called on, so drive the
        // actual selection the way a real D-pad would once focus is set.
        composeRule.onNodeWithTag(NAV_TAG_SEARCH).requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TAG).performTextInput("Test Song")
        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TAG).performImeAction()

        composeRule.onNodeWithText("Sample Song").assertIsDisplayed()
        // TV Material3's Card only wires its onClick to real key/remote input, not Compose test's
        // semantics-based performClick(), so select it the way a D-pad actually would.
        composeRule.onNodeWithText("Sample Song").performKeyInput { pressKey(Key.DirectionCenter) }

        // The player's controls are hidden by default; reveal them to show the now-playing title.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Sample Song").assertIsDisplayed()

        // Getting back to Search can take a variable number of Back presses: one is consumed
        // hiding the controls (unless the 3s auto-hide timer already beat us to it), one
        // navigates back -- and on some CI emulators, a Back key event is occasionally dropped
        // entirely (observed directly: no key ever reaches the app). Retry instead of assuming a
        // fixed count, bounded so a real regression still fails fast.
        var attempts = 0
        while (attempts < MAX_BACK_PRESS_ATTEMPTS &&
            composeRule.onAllNodesWithTag(SEARCH_QUERY_FIELD_TAG).fetchSemanticsNodes().isEmpty()
        ) {
            Espresso.pressBack()
            attempts++
        }

        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TAG).assertIsDisplayed()
    }
}
