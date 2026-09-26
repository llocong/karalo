package com.karalo.core.ui.testing

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry

private const val KEYBOARD_MODE_TIMEOUT_MS = 5_000L

/**
 * [ComposeContentTestRule.setContent] for tests that drive focus with the D-pad, the way a TV
 * remote does.
 *
 * An emulator boots in touch mode, and in touch mode Compose lets a `clickable` item take focus
 * only through an explicit request: arrow-key focus search finds nothing to move to, so presses
 * sent with `performKeyInput` do nothing. Those presses never leave touch mode either, since they
 * don't go through Android's input pipeline. A TV is never in touch mode, so this leaves it the way
 * a remote does: one real D-pad press through that pipeline, sent before the content exists so it
 * can't move focus anywhere (Android only swallows the press that leaves touch mode when nothing is
 * focused yet), then waits until the composition sees keyboard mode.
 * Touch mode is device-wide, so without this a test's result depends on whatever touched the
 * emulator last (every CI run starts from a fresh boot, in touch mode).
 */
fun ComposeContentTestRule.setKeyboardModeContent(content: @Composable () -> Unit) {
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
    var inputMode: InputMode? = null
    setContent {
        inputMode = LocalInputModeManager.current.inputMode
        content()
    }
    waitUntil(KEYBOARD_MODE_TIMEOUT_MS) { inputMode == InputMode.Keyboard }
}
