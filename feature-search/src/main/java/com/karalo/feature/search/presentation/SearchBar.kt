package com.karalo.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

// Safe-zone content margin (developer.android.com/design/ui/tv/guides/styles/layouts), duplicated
// locally per this codebase's established convention (see HomeScreen.kt/SearchResultsRow.kt).
private val SAFE_ZONE_HORIZONTAL = 58.dp
private val MIC_BUTTON_SIZE = 56.dp
private val MIC_TO_FIELD_GAP = 16.dp
private val SEARCH_FIELD_HORIZONTAL_PADDING = 24.dp
private val SEARCH_FIELD_VERTICAL_PADDING = 16.dp
private const val SEARCH_PLACEHOLDER = "Search for a song or artist"

/**
 * The search bar row: a circular mic button followed by a pill-shaped query field, filling the
 * safe-zone width. Deliberately flat black/white/gray -- see [SearchMonochromeColors] -- unlike the
 * rest of the app's violet-tinted [MaterialTheme.colorScheme], with the mic button's purple fill as
 * the one intentional exception (the app's existing brand accent, not a new color).
 */
@Composable
internal fun SearchBar(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    hasSuggestions: Boolean,
    onDownToSuggestions: () -> Unit,
    railFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    onMicClick: () -> Unit = {},
) {
    val micFocusRequester = remember { FocusRequester() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = SAFE_ZONE_HORIZONTAL),
    ) {
        MicButton(
            onClick = onMicClick,
            railFocusRequester = railFocusRequester,
            focusRequester = micFocusRequester,
        )
        Box(modifier = Modifier.width(MIC_TO_FIELD_GAP))
        SearchQueryField(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = onTextFieldValueChange,
            onSubmit = onSubmit,
            focusRequester = focusRequester,
            hasSuggestions = hasSuggestions,
            onDownToSuggestions = onDownToSuggestions,
            micFocusRequester = micFocusRequester,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A placeholder voice-search entry point -- visual only for now, [onClick] is a no-op until real
 * speech recognition is wired up. Kept as a real, focusable D-pad target rather than excluded from
 * focus order: a TV remote has no touch/click concept, so a "does nothing yet" button still needs
 * to be reachable to be meaningfully a placeholder at all.
 */
@Composable
private fun MicButton(
    onClick: () -> Unit,
    railFocusRequester: FocusRequester?,
    focusRequester: FocusRequester,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = Color.White,
                pressedContainerColor = MaterialTheme.colorScheme.primary,
                pressedContentColor = Color.White,
            ),
        modifier =
            Modifier
                .size(MIC_BUTTON_SIZE)
                .focusRequester(focusRequester)
                // The mic button is the true leftmost item in Search's content -- LEFT here always
                // opens the drawer with Search's own item focused, matching the established
                // "leftmost edge of content deterministically reaches the drawer" convention used
                // everywhere else (e.g. SearchResultsRow's first-card override) rather than relying
                // on Compose's default spatial focus search.
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.DirectionLeft &&
                        railFocusRequester != null
                    ) {
                        railFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.Mic, contentDescription = "Voice search")
        }
    }
}

@Composable
private fun SearchQueryField(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    hasSuggestions: Boolean,
    onDownToSuggestions: () -> Unit,
    micFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val isEmpty = textFieldValue.text.isEmpty()
    // Uses titleMedium (the Plain/Manrope role) rather than a Brand/Fredoka style: an editable
    // text field needs a plain, highly-legible face for arbitrary typed text, not the expressive
    // display font reserved for headlines.
    //
    // The TextFieldValue overload (rather than the plain String one) is deliberate: it's the only
    // way to control cursor *position* explicitly, needed so that setting the field's text from a
    // picked suggestion (see SearchScreenContent's onSuggestionClick) can place the cursor at the
    // end of the new text rather than wherever it happened to land from the previous value.
    BasicTextField(
        value = textFieldValue,
        onValueChange = onTextFieldValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = SearchTypedText),
        cursorBrush = SolidColor(SearchTypedText),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (isEmpty) {
                    Text(
                        text = SEARCH_PLACEHOLDER,
                        style = MaterialTheme.typography.titleMedium,
                        color = SearchPlaceholderText,
                    )
                }
                innerTextField()
            }
        },
        modifier =
            modifier
                .testTag(SEARCH_QUERY_FIELD_TAG)
                .focusRequester(focusRequester)
                // BasicTextField unconditionally consumes DirectionLeft/Right itself for cursor
                // movement -- even when the cursor is already at the very start of an empty field,
                // where moving it left has no effect -- so Compose's default focus search never
                // even gets a chance to reach the mic button via LEFT. Intercepted here instead,
                // gated on the field being empty (the only state where redirecting LEFT away can
                // never interrupt a legitimate in-progress cursor move).
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else if (hasSuggestions && keyEvent.key == Key.DirectionDown) {
                        onDownToSuggestions()
                        true
                    } else if (isEmpty && keyEvent.key == Key.DirectionLeft) {
                        micFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                }.background(SearchPillFill, RoundedCornerShape(percent = 50))
                .padding(horizontal = SEARCH_FIELD_HORIZONTAL_PADDING, vertical = SEARCH_FIELD_VERTICAL_PADDING),
    )
}

const val SEARCH_QUERY_FIELD_TAG = "search_query_field"
