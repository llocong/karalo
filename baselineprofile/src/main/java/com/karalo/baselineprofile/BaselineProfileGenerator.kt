package com.karalo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val TARGET_PACKAGE = "com.karalo.karalo"
private const val SHELF_BROWSE_PRESSES = 8
private const val UI_SETTLE_TIMEOUT_MS = 3_000L

/**
 * Generates `:app`'s Baseline Profile by driving the real app through its most jank-sensitive
 * real-device flow: browsing Home's shelves one card at a time with discrete D-pad presses -- the
 * exact interaction found, via `adb shell dumpsys gfxinfo`/systrace on a real Chromecast with
 * Google TV, to spend tens of milliseconds per frame with ART JIT-compiling Compose's own
 * measure/layout code live, mid-scroll. A Baseline Profile only records *which* classes/methods
 * ran, not per-frame timing, so this doesn't need to run on real (slow) hardware to be effective --
 * the emulator and the real TV produce an equivalent profile; the emulator is used here for
 * reproducible, CI-independent generation (see `baselineProfile { automaticGenerationDuringBuild
 * = false }` in :app's build file for why this still isn't run automatically in CI).
 *
 * Deliberately doesn't attempt to cover every screen (Search, the player, etc.) -- a Baseline
 * Profile is meant to warm the startup + first-impression path, not the whole app; broadening it
 * indefinitely mostly just bloats the profile without a proportional real-world benefit. Extend
 * this if profiling turns up another flow with the same "live JIT compile mid-interaction" issue.
 */
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_SETTLE_TIMEOUT_MS)

            // Home's one shelf (Top Picks, or Halloween Hits): focused and visible on launch.
            repeat(SHELF_BROWSE_PRESSES) {
                device.pressDPadRight()
                device.waitForIdle()
            }
        }
}
