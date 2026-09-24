package com.karalo.feature.history

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** LEFT moves focus to [railFocusRequester] (when there is one) instead of searching spatially. */
internal fun Modifier.leftGoesTo(railFocusRequester: FocusRequester?): Modifier =
    if (railFocusRequester == null) {
        this
    } else {
        onPreviewKeyEvent { keyEvent ->
            val isLeft = keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft
            if (isLeft) railFocusRequester.requestFocus()
            isLeft
        }
    }

/**
 * BACK closes an open panel, otherwise goes to the rail -- a raw key event rather than a
 * BackHandler, for the reason given on HomeScreenContent's own BACK handling.
 */
internal fun Modifier.backClosesPanelOrGoesTo(
    panelOpen: Boolean,
    onDismissPanel: () -> Unit,
    railFocusRequester: FocusRequester?,
): Modifier =
    onPreviewKeyEvent { keyEvent ->
        val isBack = keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back
        when {
            !isBack -> false
            panelOpen -> {
                onDismissPanel()
                true
            }
            railFocusRequester != null -> {
                railFocusRequester.requestFocus()
                true
            }
            else -> false
        }
    }

/**
 * UP from the newest song goes to the first button (Sort) rather than whichever button happens
 * to sit closest to the row's center, so the buttons are always entered from the left.
 */
internal fun Modifier.upGoesTo(target: FocusRequester): Modifier =
    onPreviewKeyEvent { keyEvent ->
        val isUp = keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp
        if (isUp) target.requestFocus()
        isUp
    }
