package com.karalo.karalo

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import com.karalo.karalo.nav.NAV_TAG_HOME
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val SCROLL_DEPTH = 40
private const val TARGET_ITEM_TEXT = "Filler Song $SCROLL_DEPTH"
private const val MAX_BACK_PRESS_ATTEMPTS = 4

/**
 * The real-device gate for TvCarousel's staged focus-restore rollout (see `TvCarousel.md`):
 * scrolls a Home shelf far enough that the focused card would no longer be composed after a fresh
 * remount, plays it, returns from the Player, and asserts focus lands back on that exact card.
 *
 * This test is exactly what proved `Modifier.focusRestorer()` alone is *not* sufficient for this
 * case (its restore only walks the currently-composed focus targets, so it silently fell back to
 * the first card here) -- `TvCarousel`'s explicit `restoreFocusItemKey`/`restoreFocusRequester`
 * mechanism, wired back in by `HomeScreenContent`/`SearchScreenContent` via the still-live
 * `playerReturnTrigger`, is what actually makes this pass. Kept as a permanent regression test, not
 * a temporary gate: if this mechanism is ever removed or broken, this is what catches it.
 *
 * Every shelf on Home is scripted with identical fake data (see `FakeYouTubeClientModule`), so a
 * card with this test's target title legitimately exists in all three shelves at once after the
 * round trip -- the assertion below deliberately looks for "a node with this text that is
 * focused" rather than assuming there's only one such node.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class TvCarouselFocusRestorationRoundTripTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun assertAFocusedNodeWithTargetTextExists() {
        composeRule
            .onNode(hasText(TARGET_ITEM_TEXT) and isFocused())
            .assertExists("expected some \"$TARGET_ITEM_TEXT\" card to be focused")
    }

    @Test
    fun focusIsRestoredToACardScrolledDeepIntoAShelfAfterAPlayerRoundTrip() {
        composeRule.onNodeWithTag(NAV_TAG_HOME).requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        repeat(SCROLL_DEPTH) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        }
        assertAFocusedNodeWithTargetTextExists()

        // TV Material's Card only wires its onClick to real key/remote input, not Compose test's
        // semantics-based performClick(), so select it the way a D-pad actually would (matching
        // KaraloNavigationTest's established convention).
        composeRule
            .onNode(hasText(TARGET_ITEM_TEXT) and isFocused())
            .performKeyInput { pressKey(Key.DirectionCenter) }

        // Getting back to Home can take a variable number of Back presses -- one may be consumed
        // hiding the player's controls first -- so retry rather than assume a fixed count, bounded
        // so a real regression still fails fast (same pattern as KaraloNavigationTest).
        var attempts = 0
        while (attempts < MAX_BACK_PRESS_ATTEMPTS &&
            composeRule.onAllNodesWithText(TARGET_ITEM_TEXT).fetchSemanticsNodes().isEmpty()
        ) {
            Espresso.pressBack()
            attempts++
        }

        assertAFocusedNodeWithTargetTextExists()
    }
}
