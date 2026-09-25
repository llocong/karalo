package com.karalo.feature.history

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.ErrorState
import com.karalo.core.ui.components.LoadingIndicator
import com.karalo.core.ui.components.SidePanel
import com.karalo.core.ui.focus.CenteredBringIntoViewSpec
import com.karalo.core.ui.theme.KaraloPagePadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private val SAFE_ZONE_HORIZONTAL = KaraloPagePadding
private val SAFE_ZONE_VERTICAL = KaraloPagePadding

// Start fetching the next page while this many entries are still below the last visible one,
// so holding DOWN rarely reaches the end of what's loaded.
private const val LOAD_MORE_THRESHOLD = 15

internal const val HISTORY_TAG_LIST = "history_list"
internal const val HISTORY_TAG_PAUSE = "history_pause"
private const val REFRESH_SETTLE_DELAY_MS = 400L

internal const val HISTORY_TAG_SORT = "history_sort"
internal const val HISTORY_TAG_CLEAR = "history_clear"
internal const val HISTORY_TAG_CANCEL = "history_cancel"

/**
 * The History page: every song played on this TV, newest first (grouped by karaoke night) or
 * most played first, with Pause/Resume and Clear. Mounted once and kept alive like the other
 * rail tabs (see MainTabsHost), so it reloads whenever it becomes [isActive] again and after a
 * return from the player ([playerReturnTrigger]) -- new plays may have landed meanwhile.
 */
@Composable
fun HistoryScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    playerReturnTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Only once History has stayed on screen for a moment: the rail previews each page it passes
    // over, and refreshing (a network call, then rebuilding the list) on every pass made scanning
    // the rail stutter on the reference TV.
    LaunchedEffect(isActive) {
        if (isActive) {
            delay(REFRESH_SETTLE_DELAY_MS)
            viewModel.refresh()
        }
    }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    var consumedPlayerReturn by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(playerReturnTrigger) {
        if (playerReturnTrigger > consumedPlayerReturn) {
            consumedPlayerReturn = playerReturnTrigger
            viewModel.refresh()
        }
    }

    HistoryScreenContent(
        uiState = uiState,
        onSongClick = { song ->
            viewModel.onSongSelected(song)
            onResultClick(0, song.videoId)
        },
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onSortClick = viewModel::openSortPanel,
        onPauseClick = viewModel::togglePaused,
        onClearClick = viewModel::openClearPanel,
        onSortSelected = viewModel::setSort,
        onConfirmClear = viewModel::confirmClear,
        onDismissPanel = viewModel::dismissPanel,
        contentFocusTrigger = contentFocusTrigger,
        playerReturnTrigger = playerReturnTrigger,
        railFocusRequester = railFocusRequester,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryScreenContent(
    uiState: HistoryUiState,
    onSongClick: (HistoryRow.Song) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSortClick: () -> Unit,
    onPauseClick: () -> Unit,
    onClearClick: () -> Unit,
    onSortSelected: (HistorySort) -> Unit,
    onConfirmClear: () -> Unit,
    onDismissPanel: () -> Unit,
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    playerReturnTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
) {
    val listState = rememberLazyListState()
    val focus = remember { HistoryFocus() }

    // The song last played from here, to put focus back on it after BACK from the player.
    var lastPlayedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestoreKey by remember { mutableStateOf<String?>(null) }
    var consumedPlayerReturn by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(playerReturnTrigger) {
        if (playerReturnTrigger > consumedPlayerReturn) {
            consumedPlayerReturn = playerReturnTrigger
            pendingRestoreKey = lastPlayedKey
        }
    }
    RestoreFocusEffect(pendingRestoreKey, uiState.rows, listState, focus.restore) { pendingRestoreKey = null }

    val hasSongs = uiState.rows.any { it is HistoryRow.Song }
    ContentFocusEffect(contentFocusTrigger, uiState.isLoading, hasSongs, listState, focus)
    PanelFocusEffect(uiState.panel, uiState.sort, hasSongs, focus)
    LoadMoreEffect(listState, uiState.rows, onLoadMore)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .backClosesPanelOrGoesTo(uiState.panel != HistoryPanel.NONE, onDismissPanel, railFocusRequester),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = SAFE_ZONE_VERTICAL)) {
            HistoryHeader(
                uiState = uiState,
                hasSongs = hasSongs,
                onSortClick = onSortClick,
                onPauseClick = onPauseClick,
                onClearClick = onClearClick,
                focus = focus,
                railFocusRequester = railFocusRequester,
            )
            when {
                uiState.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
                uiState.errorMessage != null -> ErrorState(message = uiState.errorMessage, onRetry = onRetry)
                uiState.isEmpty -> HistoryEmptyState(paused = uiState.paused)
                else ->
                    HistoryList(
                        rows = uiState.rows,
                        listState = listState,
                        firstSongRequester = focus.firstSong,
                        sortButtonRequester = focus.sortButton,
                        restoreKey = pendingRestoreKey,
                        restoreRequester = focus.restore,
                        railFocusRequester = railFocusRequester,
                        onSongClick = { song ->
                            lastPlayedKey = song.key
                            onSongClick(song)
                        },
                    )
            }
        }

        SidePanel(visible = uiState.panel == HistoryPanel.SORT, onDismiss = onDismissPanel) {
            SortPanelContent(current = uiState.sort, requesters = focus.sortOptions, onSelect = onSortSelected)
        }
        SidePanel(visible = uiState.panel == HistoryPanel.CLEAR_CONFIRM, onDismiss = onDismissPanel) {
            ClearPanelContent(cancelRequester = focus.cancel, onCancel = onDismissPanel, onConfirm = onConfirmClear)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(
    rows: List<HistoryRow>,
    listState: LazyListState,
    firstSongRequester: FocusRequester,
    sortButtonRequester: FocusRequester,
    restoreKey: String?,
    restoreRequester: FocusRequester,
    railFocusRequester: FocusRequester?,
    onSongClick: (HistoryRow.Song) -> Unit,
) {
    val firstSongIndex = rows.indexOfFirst { it is HistoryRow.Song }
    CompositionLocalProvider(LocalBringIntoViewSpec provides CenteredBringIntoViewSpec) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(HISTORY_TAG_LIST)
                    // Coming back DOWN from the buttons lands on the row that last had focus.
                    .focusRestorer(firstSongRequester)
                    .leftGoesTo(railFocusRequester),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = SAFE_ZONE_HORIZONTAL,
                    end = SAFE_ZONE_HORIZONTAL,
                    bottom = SAFE_ZONE_VERTICAL,
                ),
        ) {
            itemsIndexed(
                items = rows,
                key = { _, row -> row.key },
                contentType = { _, row -> if (row is HistoryRow.Header) "header" else "song" },
            ) { index, row ->
                when (row) {
                    is HistoryRow.Header -> HistoryHeaderRow(label = row.label, isFirst = index == 0)
                    is HistoryRow.Song ->
                        HistorySongRow(
                            song = row,
                            onClick = { onSongClick(row) },
                            modifier =
                                when {
                                    row.key == restoreKey -> Modifier.focusRequester(restoreRequester)
                                    index == firstSongIndex ->
                                        Modifier.focusRequester(firstSongRequester).upGoesTo(sortButtonRequester)
                                    else -> Modifier
                                },
                        )
                }
            }
        }
    }
}

@Composable
private fun HistoryHeaderRow(
    label: String,
    isFirst: Boolean,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = if (isFirst) 4.dp else 20.dp, bottom = 6.dp),
    )
}

/** Every focus target on the page that something needs to move focus to directly. */
internal class HistoryFocus {
    val sortButton = FocusRequester()
    val pauseButton = FocusRequester()
    val clearButton = FocusRequester()
    val firstSong = FocusRequester()
    val restore = FocusRequester()
    val sortOptions = HistorySort.entries.associateWith { FocusRequester() }
    val cancel = FocusRequester()
}

/** Once [key]'s row is in [rows], scrolls to it and focuses it (via [requester]), then calls [onDone]. */
@Composable
private fun RestoreFocusEffect(
    key: String?,
    rows: List<HistoryRow>,
    listState: LazyListState,
    requester: FocusRequester,
    onDone: () -> Unit,
) {
    LaunchedEffect(key, rows) {
        val index = key?.let { k -> rows.indexOfFirst { it.key == k } } ?: -1
        if (index >= 0) {
            listState.scrollToItem(index)
            withFrameNanos { }
            requester.requestFocus()
            onDone()
        }
    }
}

/**
 * Selecting History in the rail moves focus into the page: onto the newest song (scrolling back
 * to the top first -- that row isn't composed while the list is scrolled far down, and a focus
 * request on it would silently do nothing), or onto Pause/Resume when there's nothing to list.
 * Waits for the first page if it's still loading.
 */
@Composable
private fun ContentFocusEffect(
    trigger: Int,
    isLoading: Boolean,
    hasSongs: Boolean,
    listState: LazyListState,
    focus: HistoryFocus,
) {
    var consumed by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(trigger, isLoading) {
        if (trigger > consumed && !isLoading) {
            consumed = trigger
            if (hasSongs) listState.scrollToItem(0)
            withFrameNanos { }
            (if (hasSongs) focus.firstSong else focus.pauseButton).requestFocus()
        }
    }
}

/** Opening a panel focuses its safe default; closing one returns focus to the button that opened it. */
@Composable
private fun PanelFocusEffect(
    panel: HistoryPanel,
    sort: HistorySort,
    hasSongs: Boolean,
    focus: HistoryFocus,
) {
    var previousPanel by remember { mutableStateOf(HistoryPanel.NONE) }
    // Read at request time, not when the panel closed: confirming a clear can empty the list in
    // between, and Clear all is disabled by then.
    val currentHasSongs by rememberUpdatedState(hasSongs)
    LaunchedEffect(panel) {
        withFrameNanos { }
        val target =
            when (panel) {
                HistoryPanel.SORT -> focus.sortOptions.getValue(sort)
                HistoryPanel.CLEAR_CONFIRM -> focus.cancel
                HistoryPanel.NONE ->
                    when (previousPanel) {
                        HistoryPanel.SORT -> focus.sortButton
                        // Clearing disables the Clear button, so focus falls back to Pause/Resume.
                        HistoryPanel.CLEAR_CONFIRM -> if (currentHasSongs) focus.clearButton else focus.pauseButton
                        HistoryPanel.NONE -> null
                    }
            }
        target?.requestFocus()
        previousPanel = panel
    }
}

/** Asks for the next page once the last visible row is within [LOAD_MORE_THRESHOLD] of the end. */
@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    rows: List<HistoryRow>,
    onLoadMore: () -> Unit,
) {
    val currentRows by rememberUpdatedState(rows)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.distinctUntilChanged()
            .collect { lastVisible -> if (lastVisible >= currentRows.size - LOAD_MORE_THRESHOLD) currentOnLoadMore() }
    }
}
