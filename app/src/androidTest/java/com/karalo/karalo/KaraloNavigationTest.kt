package com.karalo.karalo

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import com.karalo.feature.search.presentation.SEARCH_QUERY_FIELD_TAG
import com.karalo.karalo.nav.NAV_TAG_SEARCH
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
        composeRule.onNodeWithTag(NAV_TAG_SEARCH).performClick()

        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TAG).performTextInput("Test Song")
        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TAG).performImeAction()

        composeRule.onNodeWithText("Sample Song").assertIsDisplayed()
        // TV Material3's Card only wires its onClick to real key/remote input, not Compose test's
        // semantics-based performClick(), so select it the way a D-pad actually would.
        composeRule.onNodeWithText("Sample Song").performKeyInput { pressKey(Key.DirectionCenter) }

        // The player's controls are hidden by default; reveal them to show the now-playing title.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Sample Song").assertIsDisplayed()

        // Whether the first Back only hides the controls (then a second navigates back to
        // Search) or lands directly on Search depends on whether the controls' 3s auto-hide
        // timer already fired first — a device-speed race, not something this test controls —
        // so press again only if the first press didn't already get us there.
        android.util.Log.d("KaraloNavDebug", "TEST: about to press back #1")
        Espresso.pressBack()
        val foundAfterFirst = composeRule.onAllNodesWithTag(SEARCH_QUERY_FIELD_TAG).fetchSemanticsNodes().isNotEmpty()
        android.util.Log.d("KaraloNavDebug", "TEST: after back #1, searchFieldFound=$foundAfterFirst")
        if (!foundAfterFirst) {
            android.util.Log.d("KaraloNavDebug", "TEST: about to press back #2")
            Espresso.pressBack()
        }
        android.util.Log.d("KaraloNavDebug", "TEST: final assertion")

        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TAG).assertIsDisplayed()
    }
}
