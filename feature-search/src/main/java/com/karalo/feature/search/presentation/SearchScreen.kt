package com.karalo.feature.search.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karalo.core.ui.components.ErrorState
import com.karalo.core.ui.components.LoadingIndicator

// Safe-zone vertical content margin recommended by the TV layout guidelines
// (developer.android.com/design/ui/tv/guides/styles/layouts) -- horizontal is applied per-child
// instead (on the search bar and each row's own contentPadding), see the Column's own comment below.
private val SAFE_ZONE_VERTICAL = 28.dp

@Composable
fun SearchScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    playerReturnTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
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
        playerReturnTrigger = playerReturnTrigger,
        railFocusRequester = railFocusRequester,
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
    playerReturnTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    // Lifted out of SearchQueryField so a suggestion click (which bypasses onQueryChanged) can
    // also update the field's displayed text — see onSuggestionClick below. Seeded with the
    // cleaned-up text since this recomposes fresh (losing any prior edits) whenever the screen
    // re-enters composition, e.g. navigating back from the player. A TextFieldValue (rather than a
    // plain String) so the cursor position can be controlled explicitly -- see onSuggestionClick's
    // own comment for why that matters.
    var textFieldValue by
        remember {
            // Cursor explicitly placed at the end of this seed text (rather than relying on
            // TextFieldValue's default TextRange.Zero) -- otherwise a remount that reseeds this with
            // non-empty restored text (e.g. navigating back from the player, then pressing UP back
            // to the field) leaves the cursor sitting at the very start of the query instead.
            val seedText = formatSuggestion(rawQueryOf(uiState))
            mutableStateOf(TextFieldValue(seedText, selection = TextRange(seedText.length)))
        }
    val focusRequester = remember { FocusRequester() }
    val firstSuggestionFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    // Bumped each time DOWN is pressed from the query field -- left to SuggestionChipsRow's/
    // SearchResultsRow's own focusFirstItem(OnDown)Trigger handling to actually move focus, rather
    // than requesting it directly here, since only they own the LazyListState needed to scroll a
    // possibly-scrolled-away first item back into view first (see their own comments for why a
    // bare requestFocus() isn't enough). Which one applies depends on what's currently showing --
    // exactly one of the two is ever non-zero-and-fresh at a time, so bumping both unconditionally
    // is harmless (the other row isn't even composed to react to its own bump).
    var focusFirstSuggestionTrigger by remember { mutableIntStateOf(0) }
    var focusFirstResultTrigger by remember { mutableIntStateOf(0) }

    // The video last clicked into, restored on a genuine return from the player (not merely
    // *focusing* Search in the rail to preview it, which also navigates here -- see
    // KaraloNavRailContent -- producing a remount that would otherwise be indistinguishable from a
    // real return) so focus lands back on it instead of defaulting to the first result.
    // Modifier.focusRestorer() alone is *not* enough for this (confirmed via a real-device
    // end-to-end test, see TvCarouselFocusRestorationRoundTripTest): its restore only walks the
    // currently-composed focus targets, so a result far enough into the row to not be composed yet
    // after this screen's own dispose/recreate cycle would silently fall back to the first result
    // instead. SearchResultsRow's restoreFocusItemKey (forwarded to TvCarousel) covers exactly this
    // case.
    var lastPlayedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocusRequester = remember { FocusRequester() }
    val trackedOnResultClick: (Int, String) -> Unit = { index, videoId ->
        lastPlayedVideoId = videoId
        onResultClick(index, videoId)
    }
    var consumedPlayerReturnTrigger by rememberSaveable { mutableIntStateOf(0) }
    val canRestoreLastPlayed = playerReturnTrigger > consumedPlayerReturnTrigger
    LaunchedEffect(playerReturnTrigger) {
        if (playerReturnTrigger > consumedPlayerReturnTrigger) {
            consumedPlayerReturnTrigger = playerReturnTrigger
        }
    }

    rememberSearchFocusState(
        uiState = uiState,
        contentFocusTrigger = contentFocusTrigger,
        focusRequester = focusRequester,
        firstResultFocusRequester = firstResultFocusRequester,
    )

    // Horizontal safe-zone inset is applied per-child below (on the query field directly, and as
    // the results row's own contentPadding) rather than here on the whole Column, so a focused
    // edge card in that row can visually scale up into the reserved contentPadding space instead
    // of being clipped by the Column's own now-narrower bounds -- matching how Home's shelves
    // avoid the same problem (see HomeScreenContent's Column, which only insets vertically for
    // exactly this reason).
    Column(
        modifier =
            modifier
                .fillMaxSize()
                // BACK while browsing opens the drawer with Search's own item focused -- see the
                // matching comment on HomeScreenContent's own Column for why this is a raw key
                // event intercept rather than a BackHandler.
                .onPreviewKeyEvent { keyEvent ->
                    val isBackKeyDown = keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back
                    if (isBackKeyDown && railFocusRequester != null) {
                        railFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                }.padding(vertical = SAFE_ZONE_VERTICAL),
    ) {
        SearchBar(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = {
                // BasicTextField can echo a spurious onValueChange carrying the *same* text back
                // to us shortly after we set textFieldValue programmatically (e.g. right after
                // picking a suggestion, see onSuggestionClick below) -- forwarding that to
                // onQueryChanged would feed it into the debounced suggestions pipeline as if the
                // user had typed it, which can resurrect the Suggesting state over Results/Loading
                // a moment later. Only text that actually *differs* from what's already held is a
                // genuine edit -- a cursor-only move (arrow keys) shares this same shape and is
                // correctly ignored here too, since it never changes the query.
                val isGenuineEdit = it.text != textFieldValue.text
                textFieldValue = it
                if (isGenuineEdit) {
                    onQueryChanged(it.text)
                }
            },
            onSubmit = onSubmit,
            focusRequester = focusRequester,
            onDownPressed = {
                when (uiState) {
                    is SearchUiState.Suggesting -> focusFirstSuggestionTrigger++
                    is SearchUiState.Results -> focusFirstResultTrigger++
                    else -> Unit
                }
            },
            railFocusRequester = railFocusRequester,
        )

        when (uiState) {
            is SearchUiState.Idle -> Unit
            is SearchUiState.Suggesting ->
                // Sits close below the search bar (its own internal top gap handles the spacing --
                // see SuggestionChipsRow's SUGGESTIONS_ROW_TOP_GAP) rather than centered in the
                // remaining space, so it reads as "between" the field and where results will
                // appear without drifting down toward the middle of the screen.
                SuggestionChipsRow(
                    suggestions = uiState.suggestions,
                    onSuggestionClick = { suggestion ->
                        // Claimed synchronously, before the state change below even reaches this
                        // composition: submitting immediately swaps this whole suggestions row out
                        // for a Loading/Results branch instead, disposing the just-clicked chip's
                        // focus node out from under real focus. Left alone, that leaves nothing in
                        // Search's own content still focused, and the drawer's own rail behind it
                        // is the nearest fallback focus target -- briefly opening the drawer until
                        // the eventual Loading -> Results transition claims focus back into content
                        // (see the isShowingResults effect below). Grabbing the query field here
                        // instead keeps focus inside this screen's content the whole time.
                        focusRequester.requestFocus()
                        // Display the cleaned-up text (matching the chip itself), but still submit
                        // the raw suggestion — see formatSuggestion's own doc. Cursor explicitly
                        // placed at the end of the new text (a plain `text = ...` on a
                        // TextFieldValue would otherwise carry the *previous* value's cursor
                        // offset/selection forward unchanged, which can land it mid-string).
                        val newText = formatSuggestion(suggestion)
                        textFieldValue = TextFieldValue(newText, selection = TextRange(newText.length))
                        onSuggestionClick(suggestion)
                    },
                    firstItemFocusRequester = firstSuggestionFocusRequester,
                    focusFirstItemTrigger = focusFirstSuggestionTrigger,
                    queryFieldFocusRequester = focusRequester,
                    railFocusRequester = railFocusRequester,
                )
            is SearchUiState.Loading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            is SearchUiState.Results ->
                // Vertically centered in the remaining space below the search bar, rather than
                // sitting flush beneath it like the suggestions row does.
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    SearchResultsRow(
                        items = uiState.items,
                        onResultClick = trackedOnResultClick,
                        firstItemFocusRequester = firstResultFocusRequester,
                        focusFirstItemTrigger = contentFocusTrigger,
                        restoreFocusItemKey = lastPlayedVideoId.takeIf { canRestoreLastPlayed },
                        restoreFocusRequester = restoreFocusRequester,
                        railFocusRequester = railFocusRequester,
                        queryFieldFocusRequester = focusRequester,
                        focusFirstItemOnDownTrigger = focusFirstResultTrigger,
                    )
                }
            is SearchUiState.Error -> ErrorState(message = uiState.message, onRetry = onSubmit)
        }
    }
}

/**
 * Owns the two remaining pieces of state that decide *where* focus moves within this screen (the
 * last-played-item restoration lives directly in [SearchScreenContent] and SearchResultsRow
 * instead, driven by a genuine return from the player rather than either case here), kept out of
 * [SearchScreenContent] purely to keep that function's own complexity down: (1) a genuine Loading
 * -> Results transition (a real query submission during this screen's lifetime) focuses the first
 * result; (2) an explicit rail-click selection -- a fresh [contentFocusTrigger] -- focuses the
 * query field when there are no results yet. When results *are* already showing, focusing the
 * first one is left entirely to SearchResultsRow's own focusFirstItemTrigger handling instead of
 * being done here, since only it owns the LazyListState needed to scroll a possibly-scrolled-away
 * first item back into view first.
 */
@Composable
private fun rememberSearchFocusState(
    uiState: SearchUiState,
    contentFocusTrigger: Int,
    focusRequester: FocusRequester,
    firstResultFocusRequester: FocusRequester,
) {
    val isShowingResults = uiState is SearchUiState.Results
    var previousIsShowingResults by remember { mutableStateOf(isShowingResults) }
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(isShowingResults) {
        if (isShowingResults && !previousIsShowingResults) {
            firstResultFocusRequester.requestFocus()
        }
        previousIsShowingResults = isShowingResults
    }

    LaunchedEffect(contentFocusTrigger) {
        if (contentFocusTrigger > consumedFocusTrigger) {
            consumedFocusTrigger = contentFocusTrigger
            val hasResults = (uiState as? SearchUiState.Results)?.items?.isNotEmpty() == true
            // Focusing an already-showing, possibly-scrolled results row needs to scroll back to
            // its first item first -- left entirely to SearchResultsRow's own focusFirstItemTrigger
            // handling (it owns the LazyListState needed to do that; see its own comment for why).
            if (!hasResults) {
                focusRequester.requestFocus()
            }
        }
    }
}

private fun rawQueryOf(uiState: SearchUiState): String =
    when (uiState) {
        is SearchUiState.Idle -> ""
        is SearchUiState.Suggesting -> uiState.rawQuery
        is SearchUiState.Loading -> uiState.rawQuery
        is SearchUiState.Results -> uiState.rawQuery
        is SearchUiState.Error -> uiState.rawQuery
    }
