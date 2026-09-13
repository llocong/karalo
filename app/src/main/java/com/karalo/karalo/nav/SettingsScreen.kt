package com.karalo.karalo.nav

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Placeholder for v1 — no settings to configure yet. Still claims focus when *selected* (clicked)
 * in the nav rail, though: with nothing else focusable in this empty content, selecting Settings
 * would otherwise have nowhere to move focus to, leaving the drawer open and looking like the
 * selection silently did nothing.
 *
 * [contentFocusTrigger] is bumped only on selection, not on merely *focusing* Settings in the rail
 * to preview it (see KaraloNavRailContent) -- claiming focus unconditionally on every mount would
 * steal it away from the rail the instant the item is focused, since focusing already navigates
 * here to preview it.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
) {
    val focusRequester = remember { FocusRequester() }
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(contentFocusTrigger) {
        if (contentFocusTrigger > consumedFocusTrigger) {
            consumedFocusTrigger = contentFocusTrigger
            focusRequester.requestFocus()
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                // BACK or LEFT while browsing opens the drawer with Settings' own item focused --
                // there being nothing else in this placeholder content to navigate to -- matching
                // Home/Search's own BACK handling (see the matching comment on
                // HomeScreenContent's own Column for why this is a raw key event intercept rather
                // than a BackHandler). LEFT is handled the same way here (unlike Home/Search, which
                // only special-case it on their own first item/column) since this screen has no
                // other content for LEFT to mean anything else.
                .onPreviewKeyEvent { keyEvent ->
                    val isBackOrLeftDown =
                        keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Back || keyEvent.key == Key.DirectionLeft)
                    if (isBackOrLeftDown && railFocusRequester != null) {
                        railFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                }.focusable(),
    )
}
