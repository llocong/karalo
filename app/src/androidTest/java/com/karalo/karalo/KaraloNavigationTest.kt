package com.karalo.karalo

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
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

        Log.d("KaraloDebug", "before first assertion")
        composeRule.onRoot(useUnmergedTree = true).printToLog("KaraloDebug")
        composeRule.onNodeWithText("Karaoke Test Song").assertIsDisplayed()

        Log.d("KaraloDebug", "before click")
        composeRule.onNodeWithText("Karaoke Test Song").performClick()

        // The player's controls overlay shows the now-playing title.
        Log.d("KaraloDebug", "before second assertion")
        composeRule.onRoot(useUnmergedTree = true).printToLog("KaraloDebug")
        composeRule.onNodeWithText("Karaoke Test Song").assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithTag(SEARCH_QUERY_FIELD_TAG).assertIsDisplayed()
    }
}
