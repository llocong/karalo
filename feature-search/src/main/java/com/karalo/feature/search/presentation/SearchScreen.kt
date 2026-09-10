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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.ErrorState
import com.karalo.core.ui.components.LoadingIndicator

@Composable
fun SearchScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SearchScreenContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onSubmit = viewModel::onSubmit,
        onSuggestionClick = { suggestion -> viewModel.onSubmit(suggestion) },
        onResultClick = onResultClick,
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
    modifier: Modifier = Modifier,
) {
    // Lifted out of SearchQueryField so a suggestion click (which bypasses onQueryChanged) can
    // also update the field's displayed text — see onSuggestionClick below.
    var text by remember { mutableStateOf(rawQueryOf(uiState)) }
    val focusRequester = remember { FocusRequester() }
    val firstSuggestionFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val hasSuggestions = uiState is SearchUiState.Suggesting && uiState.suggestions.isNotEmpty()
    val isShowingResults = uiState is SearchUiState.Results

    LaunchedEffect(isShowingResults) {
        if (isShowingResults) firstResultFocusRequester.requestFocus()
    }

    Column(modifier = modifier.fillMaxSize().padding(32.dp)) {
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
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle =
            TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
            ),
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
                }.background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
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
