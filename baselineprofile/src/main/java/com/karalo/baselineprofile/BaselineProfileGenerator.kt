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
private const val PLAYLIST_BROWSE_PRESSES = 6
private const val RAIL_ITEMS_ABOVE_HISTORY = 3
private const val PAGE_LOAD_WAIT_MS = 2_500L

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
 * Also covers moving through the rail and browsing the Playlists page (covers, then one
 * playlist's songs), and opening History and Search: measured on the Chromecast on 2026-09-25,
 * a first pass over flows missing from the profile missed about twice as many frame deadlines
 * as later passes, the same live-JIT cost. The player isn't covered: it needs a real video.
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
            repeat(SHELF_BROWSE_PRESSES) { press { device.pressDPadRight() } }

            // Back to the rail (on Home), where focusing an item previews its page: Playlists is
            // just below Home. OK moves into its row of covers, DOWN into the focused one's songs.
            repeat(SHELF_BROWSE_PRESSES + 1) { press { device.pressDPadLeft() } }
            press { device.pressDPadDown() }
            settle()
            press { device.pressDPadCenter() }
            repeat(PLAYLIST_BROWSE_PRESSES) { press { device.pressDPadRight() } }
            settle()
            press { device.pressDPadDown() }
            repeat(PLAYLIST_BROWSE_PRESSES) { press { device.pressDPadRight() } }

            // History (below Playlists), then Search (at the top of the rail).
            press { device.pressBack() }
            press { device.pressBack() }
            press { device.pressDPadDown() }
            settle()
            repeat(RAIL_ITEMS_ABOVE_HISTORY) { press { device.pressDPadUp() } }
            settle()
        }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.press(action: () -> Unit) {
        action()
        device.waitForIdle()
    }

    // Lets a page's first search (Playlists, Search's suggestions) come back before moving on.
    private fun androidx.benchmark.macro.MacrobenchmarkScope.settle() {
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_SETTLE_TIMEOUT_MS)
        Thread.sleep(PAGE_LOAD_WAIT_MS)
    }
}
