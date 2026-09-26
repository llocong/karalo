package com.karalo.karalo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.ui.testing.setKeyboardModeContent
import com.karalo.core.ui.theme.KaraloTheme
import com.karalo.feature.update.domain.ReleaseNotes
import com.karalo.feature.update.domain.UpdateRelease
import com.karalo.feature.update.domain.UpdateState
import com.karalo.feature.update.ui.UPDATE_TAG_PRIMARY
import com.karalo.feature.update.ui.UPDATE_TAG_WHATS_NEW
import com.karalo.feature.update.ui.WHATS_NEW_TAG_ACTION
import com.karalo.feature.update.ui.WHATS_NEW_TAG_MORE
import com.karalo.feature.update.ui.WHATS_NEW_TAG_NOTES
import com.karalo.karalo.nav.SETTINGS_TAG_THEME_PREFIX
import com.karalo.karalo.nav.SettingsScreenContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

private val RELEASE =
    UpdateRelease(
        versionCode = 4000,
        version = "0.4.0",
        releaseDate = LocalDate.of(2026, 10, 2),
        apkUrl = "https://karalo.app/download/karalo-0.4.0.apk",
        sha256 = "0".repeat(64),
        sizeBytes = 8_500_000,
        notes =
            ReleaseNotes(
                // Long enough to need scrolling on a 1080p TV.
                new = (1..8).map { "New thing number $it, described in a full sentence." },
                improved = (1..6).map { "Improvement number $it." },
                fixed = (1..8).map { "Fix number $it, for something that used to go wrong." },
            ),
    )

/** The update card at the top of Settings and the What's new page, driven with the D-pad. */
@OptIn(ExperimentalTestApi::class)
class UpdateSettingsFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var updateState: UpdateState by mutableStateOf(UpdateState.Available(RELEASE))
    private var updateNowClicks = 0

    private fun setContent() {
        composeRule.setKeyboardModeContent {
            KaraloTheme {
                SettingsScreenContent(
                    seasonalTheme = SeasonalTheme.DEFAULT,
                    onThemeSelected = {},
                    updateState = updateState,
                    installedVersion = "0.3.0",
                    onUpdateNow = { updateNowClicks++ },
                    contentFocusTrigger = 1,
                )
            }
        }
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun tag(tag: String) = composeRule.onNodeWithTag(tag)

    @Test
    fun theCardTakesFocusFirstAndTheDpadMovesAcrossAndDown() {
        setContent()
        composeRule.onNodeWithText("Version 0.4.0 is available").assertExists()
        composeRule.onNodeWithText("You have version 0.3.0").assertExists()
        tag(UPDATE_TAG_PRIMARY).assertIsFocused()

        press(Key.DirectionRight)
        tag(UPDATE_TAG_WHATS_NEW).assertIsFocused()
        press(Key.DirectionLeft)
        tag(UPDATE_TAG_PRIMARY).assertIsFocused()

        press(Key.DirectionDown)
        tag(SETTINGS_TAG_THEME_PREFIX + SeasonalTheme.DEFAULT.wireName).assertIsFocused()
        press(Key.DirectionUp)
        tag(UPDATE_TAG_PRIMARY).assertIsFocused()

        press(Key.DirectionCenter)
        assertEquals(1, updateNowClicks)
    }

    @Test
    fun startingTheDownloadMovesFocusToWhatsNew() {
        setContent()
        tag(UPDATE_TAG_PRIMARY).assertIsFocused()
        press(Key.DirectionCenter)
        // What the repository does on Update now: the card switches to downloading.
        updateState = UpdateState.Downloading(RELEASE, downloadedBytes = 0, totalBytes = 8_500_000)
        composeRule.waitForIdle()

        tag(UPDATE_TAG_PRIMARY).assertDoesNotExist()
        tag(UPDATE_TAG_WHATS_NEW).assertIsFocused()
    }

    @Test
    fun whileDownloadingWhatsNewIsTheOnlyButtonAndHasFocus() {
        updateState = UpdateState.Downloading(RELEASE, downloadedBytes = 4_250_000, totalBytes = 8_500_000)
        setContent()
        composeRule.onNodeWithText("50% · 4.3 of 8.5 MB · You can keep singing").assertExists()
        tag(UPDATE_TAG_WHATS_NEW).assertIsFocused()
        tag(UPDATE_TAG_PRIMARY).assertDoesNotExist()
    }

    @Test
    fun whatsNewScrollsItsNotesAndBackRestoresFocus() {
        setContent()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        composeRule.onNodeWithText("What’s new in 0.4.0").assertExists()
        composeRule.onNodeWithText("Released October 2, 2026").assertExists()
        tag(WHATS_NEW_TAG_ACTION).assertIsFocused()
        tag(WHATS_NEW_TAG_MORE).assertExists()

        press(Key.DirectionDown)
        tag(WHATS_NEW_TAG_NOTES).assertIsFocused()
        // Scroll to the end: the "More below" chip goes away.
        repeat(12) { press(Key.DirectionDown) }
        tag(WHATS_NEW_TAG_MORE).assertDoesNotExist()
        // Back up to the top, then one more UP returns to the button.
        repeat(12) { press(Key.DirectionUp) }
        tag(WHATS_NEW_TAG_ACTION).assertIsFocused()

        press(Key.Back)
        tag(UPDATE_TAG_WHATS_NEW).assertIsFocused()
    }

    @Test
    fun readyToRestartOffersRestartNow() {
        updateState = UpdateState.ReadyToRestart(RELEASE, File("karalo-0.4.0.apk"))
        setContent()
        composeRule.onNodeWithText("Update ready").assertExists()
        composeRule.onNodeWithText("Restart Karalo to finish installing version 0.4.0").assertExists()
        composeRule.onNodeWithText("Restart now").assertExists()
        tag(UPDATE_TAG_PRIMARY).assertIsFocused()
    }

    @Test
    fun upToDateHasNoCardAndSaysSo() {
        updateState = UpdateState.UpToDate
        setContent()
        tag(UPDATE_TAG_PRIMARY).assertDoesNotExist()
        composeRule.onNodeWithText("0.3.0 · Up to date").assertExists()
    }
}
