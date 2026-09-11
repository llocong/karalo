package com.karalo.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.ErrorState
import com.karalo.core.ui.components.LoadingIndicator

// Safe-zone content margins recommended by the TV layout guidelines
// (developer.android.com/design/ui/tv/guides/styles/layouts).
private val SAFE_ZONE_HORIZONTAL = 58.dp
private val SAFE_ZONE_VERTICAL = 28.dp
private val SEARCH_FIELD_CORNER_RADIUS = 12.dp

@Composable
fun SearchScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SearchScreenContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onSubmit = viewModel::onSubmit,
        onSuggestionClick = { suggestion -> viewModel.onSubmit(suggestion) },
        onResultClick = onResultClick,
        contentFocusTrigger = contentFocusTrigger,
        modifier = modifier,
    )
}

@Composable
internal fun SearchScreenContent(
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onResultClick: (Int, String) -> Unit,
    contentFocusTrigger: Int,
    modifier: Modifier = Modifier,
) {
    // Lifted out of SearchQueryField so a suggestion click (which bypasses onQueryChanged) can
    // also update the field's displayed text — see onSuggestionClick below. Seeded with the
    // cleaned-up text since this recomposes fresh (losing any prior `text` edits) whenever the
    // screen re-enters composition, e.g. navigating back from the player.
    var text by remember { mutableStateOf(formatSuggestion(rawQueryOf(uiState))) }
    val focusRequester = remember { FocusRequester() }
    val firstSuggestionFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val hasSuggestions = uiState is SearchUiState.Suggesting && uiState.suggestions.isNotEmpty()
    val isShowingResults = uiState is SearchUiState.Results

    // Auto-focuses the first result only on a genuine Loading -> Results transition (a real query
    // submission during this screen's lifetime) -- seeding `previousIsShowingResults` from the
    // *current* value means mounting directly into an already-loaded Results state (e.g. merely
    // *focusing*, not selecting, the Search nav item to preview it -- see KaraloNavRailContent)
    // never steals focus away from the rail.
    var previousIsShowingResults by remember { mutableStateOf(isShowingResults) }
    LaunchedEffect(isShowingResults) {
        if (isShowingResults && !previousIsShowingResults) {
            firstResultFocusRequester.requestFocus()
        }
        previousIsShowingResults = isShowingResults
    }

    // Bumped when the user *selects* (clicks) the Search nav item -- see KaraloNavRailContent --
    // asking this screen to grab focus: the query field if there's no existing result, or the
    // first result if one is already showing. Persisted across the Compose-Navigation
    // dispose/recreate cycle that happens every time this screen is re-entered so a later,
    // unrelated recomposition -- or merely *focusing* Search in the rail, not selecting it -- never
    // mistakes an already-consumed trigger value for a fresh one.
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(contentFocusTrigger) {
        if (contentFocusTrigger > consumedFocusTrigger) {
            consumedFocusTrigger = contentFocusTrigger
            val resultsWithItems = (uiState as? SearchUiState.Results)?.takeIf { it.items.isNotEmpty() }
            if (resultsWithItems != null) {
                firstResultFocusRequester.requestFocus()
            } else {
                focusRequester.requestFocus()
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = SAFE_ZONE_HORIZONTAL, vertical = SAFE_ZONE_VERTICAL),
    ) {
        SearchQueryField(
            text = text,
            onTextChange = {
                text = it
                onQueryChanged(it)
            },
            onSubmit = onSubmit,
            focusRequester = focusRequester,
            hasSuggestions = hasSuggestions,
            onDownToSuggestions = { firstSuggestionFocusRequester.requestFocus() },
        )

        when (uiState) {
            is SearchUiState.Idle -> Unit
            is SearchUiState.Suggesting ->
                SuggestionsList(
                    suggestions = uiState.suggestions,
                    onSuggestionClick = { suggestion ->
                        // Display the cleaned-up text (matching the suggestion row itself), but
                        // still submit the raw suggestion — see formatSuggestion's own doc.
                        text = formatSuggestion(suggestion)
                        onSuggestionClick(suggestion)
                    },
                    firstItemFocusRequester = firstSuggestionFocusRequester,
                )
            is SearchUiState.Loading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            is SearchUiState.Results ->
                SearchResultsGrid(
                    items = uiState.items,
                    onResultClick = onResultClick,
                    firstItemFocusRequester = firstResultFocusRequester,
                )
            is SearchUiState.Error -> ErrorState(message = uiState.message, onRetry = onSubmit)
        }
    }
}

@Composable
private fun SearchQueryField(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    hasSuggestions: Boolean,
    onDownToSuggestions: () -> Unit,
) {
    // Uses titleMedium (the Plain/Manrope role) rather than a Brand/Fredoka style: an editable
    // text field needs a plain, highly-legible face for arbitrary typed text, not the expressive
    // display font reserved for headlines.
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(SEARCH_QUERY_FIELD_TAG)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (hasSuggestions && keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                        onDownToSuggestions()
                        true
                    } else {
                        false
                    }
                }.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(SEARCH_FIELD_CORNER_RADIUS))
                .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

const val SEARCH_QUERY_FIELD_TAG = "search_query_field"

@Composable
private fun SuggestionsList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    firstItemFocusRequester: FocusRequester,
) {
    if (suggestions.isEmpty()) return
    LazyColumn(contentPadding = PaddingValues(top = 16.dp)) {
        itemsIndexed(suggestions) { index, suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) },
                modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Text(
        text = formatSuggestion(suggestion),
        style = MaterialTheme.typography.bodyLarge,
        color =
            if (isFocused) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
                .background(if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private fun rawQueryOf(uiState: SearchUiState): String =
    when (uiState) {
        is SearchUiState.Idle -> ""
        is SearchUiState.Suggesting -> uiState.rawQuery
        is SearchUiState.Loading -> uiState.rawQuery
        is SearchUiState.Results -> uiState.rawQuery
        is SearchUiState.Error -> uiState.rawQuery
    }
