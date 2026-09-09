package com.karalo.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxSize().padding(32.dp)) {
        SearchQueryField(
            uiState = uiState,
            onQueryChanged = onQueryChanged,
            onSubmit = onSubmit,
            focusRequester = focusRequester,
        )

        when (uiState) {
            is SearchUiState.Idle -> Unit
            is SearchUiState.Suggesting -> SuggestionsList(uiState.suggestions, onSuggestionClick)
            is SearchUiState.Loading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            is SearchUiState.Results -> SearchResultsGrid(items = uiState.items, onResultClick = onResultClick)
            is SearchUiState.Error -> ErrorState(message = uiState.message, onRetry = onSubmit)
        }
    }
}

@Composable
private fun SearchQueryField(
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
) {
    var text by remember { mutableStateOf(rawQueryOf(uiState)) }

    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onQueryChanged(it)
        },
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
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
    )
}

const val SEARCH_QUERY_FIELD_TAG = "search_query_field"

@Composable
private fun SuggestionsList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    LazyColumn(contentPadding = PaddingValues(top = 16.dp)) {
        items(suggestions) { suggestion ->
            SuggestionRow(suggestion = suggestion, onClick = { onSuggestionClick(suggestion) })
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: String,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Text(
        text = suggestion,
        color =
            if (isFocused) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .focusable()
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
