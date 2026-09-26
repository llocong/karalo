package com.karalo.karalo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.ui.testing.setKeyboardModeContent
import com.karalo.core.ui.theme.KaraloTheme
import com.karalo.karalo.nav.SETTINGS_TAG_THEME_PREFIX
import com.karalo.karalo.nav.SettingsScreenContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsScreenFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var theme by mutableStateOf(SeasonalTheme.DEFAULT)

    private fun setContent() {
        composeRule.setKeyboardModeContent {
            KaraloTheme(seasonalTheme = theme) {
                SettingsScreenContent(
                    seasonalTheme = theme,
                    onThemeSelected = { theme = it },
                    contentFocusTrigger = 1,
                )
            }
        }
    }

    private fun option(theme: SeasonalTheme) = composeRule.onNodeWithTag(SETTINGS_TAG_THEME_PREFIX + theme.wireName)

    @Test
    fun selectingSettingsFocusesTheAppliedThemeAndOkSwitchesTheme() {
        setContent()
        option(SeasonalTheme.DEFAULT).assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        option(SeasonalTheme.HALLOWEEN).assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals(SeasonalTheme.HALLOWEEN, theme)
    }

    @Test
    fun upFromTheFirstOptionKeepsFocusInSettings() {
        setContent()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        option(SeasonalTheme.DEFAULT).assertIsFocused()
    }
}
