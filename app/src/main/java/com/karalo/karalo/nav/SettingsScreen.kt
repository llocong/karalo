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
) {
    val focusRequester = remember { FocusRequester() }
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(contentFocusTrigger) {
        if (contentFocusTrigger > consumedFocusTrigger) {
            consumedFocusTrigger = contentFocusTrigger
            focusRequester.requestFocus()
        }
    }
    Box(modifier = modifier.fillMaxSize().focusRequester(focusRequester).focusable())
}
