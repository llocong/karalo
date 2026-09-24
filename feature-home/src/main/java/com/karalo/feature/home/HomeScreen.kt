package com.karalo.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.core.ui.components.LoadingIndicator
import com.karalo.core.ui.components.TvCarousel
import com.karalo.core.ui.components.TvCarouselImagePrefetch
import com.karalo.core.ui.focus.InstantBringIntoViewSpec
import com.karalo.feature.search.domain.SearchResultItem
import kotlinx.coroutines.delay

// Safe-zone content margins recommended by the TV layout guidelines
// (developer.android.com/design/ui/tv/guides/styles/layouts). The bottom gets extra breathing
// room on top of that so the last shelf's focused (scaled-up) card never touches the screen edge.
// See the firstVideoFocusTrigger effect's own doc for why this retry exists.
private const val FIRST_VIDEO_FOCUS_RETRY_DELAY_MS = 50L

private val SAFE_ZONE_HORIZONTAL = 58.dp
private val SAFE_ZONE_VERTICAL = 28.dp
private val SAFE_ZONE_BOTTOM_EXTRA = 24.dp
private val BOTTOM_SPACER_HEIGHT = SAFE_ZONE_VERTICAL + SAFE_ZONE_BOTTOM_EXTRA

private val SHELF_SPACING = 32.dp
private val SHELF_TITLE_SPACING = 20.dp
private val SHELF_CARD_WIDTH = 240.dp
private val SHELF_LOADING_HEIGHT = 200.dp
private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f

private const val TOP_PICKS_TITLE = "Top Picks"
private const val POP_TITLE = "Pop"
private const val ROCK_TITLE = "Rock"

@Composable
fun HomeScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    firstVideoFocusTrigger: Int = 0,
    playerReturnTrigger: Int = 0,
    homeReselectTrigger: Int = 0,
    claimInitialPlaceholderFocus: Boolean = false,
    railFocusRequester: FocusRequester? = null,
    firstVideoFocusRequester: FocusRequester? = null,
    // Null until the karaoke session is known (briefly at launch, or for as long as the backend is
    // unreachable) -- the join banner is simply absent until then.
    sessionJoinUrl: String? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onResultClick = { items, index ->
            viewModel.onResultClicked(items)
            onResultClick(index, items[index].videoId)
        },
        firstVideoFocusTrigger = firstVideoFocusTrigger,
        playerReturnTrigger = playerReturnTrigger,
        homeReselectTrigger = homeReselectTrigger,
        claimInitialPlaceholderFocus = claimInitialPlaceholderFocus,
        railFocusRequester = railFocusRequester,
        firstVideoFocusRequester = firstVideoFocusRequester ?: remember { FocusRequester() },
        sessionJoinUrl = sessionJoinUrl,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
    firstVideoFocusTrigger: Int,
    playerReturnTrigger: Int = 0,
    homeReselectTrigger: Int = 0,
    claimInitialPlaceholderFocus: Boolean = false,
    railFocusRequester: FocusRequester? = null,
    firstVideoFocusRequester: FocusRequester = remember { FocusRequester() },
    sessionJoinUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    // The video last clicked into, restored on a genuine return from the player (not merely
    // *focusing* Home in the rail to preview it, which also navigates here -- see
    // KaraloNavRailContent -- producing a remount that would otherwise be indistinguishable from a
    // real return) so focus lands back on it instead of defaulting to the first card.
    // Modifier.focusRestorer() alone is *not* enough for this (confirmed via a real-device
    // end-to-end test): its restore only walks the currently-composed focus targets, so a card far
    // enough into a shelf to not be composed yet after this screen's own dispose/recreate cycle
    // would silently fall back to the first card instead. TvCarousel's restoreFocusItemKey covers
    // exactly this case -- see its own doc.
    var lastPlayedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocusRequester = remember { FocusRequester() }
    val trackedOnResultClick: (List<SearchResultItem>, Int) -> Unit = { items, index ->
        lastPlayedVideoId = items[index].videoId
        onResultClick(items, index)
    }
    // Restoring only on a genuine, not-yet-consumed playerReturnTrigger (rather than e.g. "no
    // fresh select pending") matters because merely *focusing* Home in the rail to preview it
    // also navigates here (see KaraloNavRailContent), producing a remount that would otherwise be
    // indistinguishable from a real return from the player.
    var consumedPlayerReturnTrigger by rememberSaveable { mutableIntStateOf(0) }
    val canRestoreLastPlayed = playerReturnTrigger > consumedPlayerReturnTrigger
    LaunchedEffect(playerReturnTrigger) {
        if (playerReturnTrigger > consumedPlayerReturnTrigger) {
            consumedPlayerReturnTrigger = playerReturnTrigger
        }
    }
    // Whichever card most recently *gained focus*, in any shelf -- not just a played one -- kept
    // up to date continuously via TvCarousel's own onItemFocused (see its doc). This is what lets
    // re-selecting Home from the rail while it's already the active destination (KaraloNavHost's
    // onHomeSelect, closing the drawer BACK opened rather than a genuine switch from elsewhere)
    // restore focus to wherever the user actually was, via the exact same restoreFocusItemKey
    // mechanism as lastPlayedVideoId above, for the identical reason native focusRestorer isn't
    // reliable enough on its own here -- confirmed on a real device: a direct requestFocus() onto
    // an ancestor .focusRestorer() group landed on the group itself (or its fallback) rather than
    // cascading down to the actual last-focused card.
    var lastFocusedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var consumedHomeReselectTrigger by rememberSaveable { mutableIntStateOf(0) }
    val canRestoreLastFocused = homeReselectTrigger > consumedHomeReselectTrigger
    LaunchedEffect(homeReselectTrigger) {
        if (homeReselectTrigger > consumedHomeReselectTrigger) {
            consumedHomeReselectTrigger = homeReselectTrigger
        }
    }
    val trackedOnItemFocused: (Any) -> Unit = { key -> (key as? String)?.let { lastFocusedVideoId = it } }
    // Neutral focus target claimed the instant this screen mounts on the app's true first-ever
    // launch, purely to keep focus off the nav rail (Compose's fallback focus-search would
    // otherwise land there -- see KaraloNavHost) until the first shelf's own first card is ready
    // to take over below. Deliberately gated rather than unconditional: claiming it on *every*
    // mount -- including a mere rail focus-preview, which also navigates here (see
    // KaraloNavRailContent) and looks identical to this from Home's own point of view -- would
    // steal real focus off the rail item the instant it's merely focused, not clicked.
    //
    // Kept genuinely, independently focusable (plain .focusable(), no .focusRestorer()) rather
    // than folded into the shelf-group's own restore target below: this placeholder must be able
    // to actually *hold* focus itself during the gap before Top Picks has loaded, when there may
    // be no focusable descendant at all yet -- a focusRestorer-bearing group node with no
    // focusable target of its own would have nothing to fall back to in that exact window.
    val placeholderFocusRequester = remember { FocusRequester() }
    if (claimInitialPlaceholderFocus) {
        LaunchedEffect(Unit) { placeholderFocusRequester.requestFocus() }
    }
    // Tracks whether the placeholder is still the thing actually holding focus while a select is
    // pending Top Picks' load (see the select-effect below) -- cleared the moment focus leaves it
    // for any reason (e.g. the user pressed BACK to the rail, or browsed into another shelf while
    // waiting) so a *later* load completion can't yank focus back into content out from under
    // whatever the user has since focused, undoing their navigation. Without this, a slow-loading
    // Top Picks could complete well after the user moved on, and the hand-off below would silently
    // steal focus back at that arbitrary later moment.
    var placeholderClaimPending by remember { mutableStateOf(false) }
    // Persisted across the Compose-Navigation dispose/recreate cycle that happens every time this
    // screen is re-entered (see KaraloNavHost's saveState/restoreState) so a later, unrelated
    // recomposition -- or simply re-entering Home by *focusing* it in the rail, not selecting it --
    // never mistakes an already-consumed trigger value for a fresh one.
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    val topPicksLoaded = (uiState.topPicks as? ShelfUiState.Loaded)?.takeIf { it.items.isNotEmpty() }

    // Reacts to a fresh (not-yet-consumed) select trigger in two steps rather than one: claim the
    // root placeholder immediately if Top Picks hasn't loaded yet, then hand off to the real first
    // video (consuming the trigger) once it has -- guaranteeing an explicit selection always
    // visibly moves focus into content and closes the drawer right away, instead of silently doing
    // nothing while Top Picks is still loading (or forever, if it fails to load at all): this
    // effect re-runs the instant topPicksLoaded flips, so the hand-off still happens as soon as it
    // can. Unlike claimInitialPlaceholderFocus above, this only fires on a genuine fresh trigger
    // (an explicit select), never a mere focus-preview, so it can safely also run on every
    // subsequent selection, not just the first-ever one.
    LaunchedEffect(firstVideoFocusTrigger, topPicksLoaded != null) {
        if (firstVideoFocusTrigger > consumedFocusTrigger) {
            if (topPicksLoaded != null) {
                consumedFocusTrigger = firstVideoFocusTrigger
                placeholderClaimPending = false
                // Retried once, after a short delay, rather than called just once: confirmed on a
                // real device that the *first* call here can silently do nothing when this trigger
                // followed a genuine return from Player (select a video -> BACK -> BACK -> RIGHT/
                // Center on Home's own rail item) -- something in that sequence leaves the rail's
                // *own* focus landing (persistently, not just a one-frame flicker) on a different
                // rail item first, and this request loses the race against that instead of ever
                // reaching this screen's first video. A second attempt a moment later reliably
                // lands it correctly once that settles.
                firstVideoFocusRequester.requestFocus()
                delay(FIRST_VIDEO_FOCUS_RETRY_DELAY_MS)
                firstVideoFocusRequester.requestFocus()
            } else {
                placeholderClaimPending = true
                placeholderFocusRequester.requestFocus()
            }
        }
    }

    // The top safe-zone inset goes *outside* verticalScroll so it's a fixed part of the viewport
    // rather than scrollable content -- otherwise it (and, per shelf, the title above each row)
    // can get scrolled out of reach: focus-driven auto-scroll only moves just enough to reveal
    // the newly-focused card, not the whole safe zone or the shelf's own title above it. The
    // bottom inset, though, is a trailing Spacer *inside* the scrollable content instead of a
    // matching fixed inset -- it should only ever be visible once you've scrolled to the last
    // shelf, not permanently shrink the viewport while you're still at the top.
    //
    // The whole page's own (vertical, shelf-to-shelf) bring-into-view scroll is instant rather
    // than animated -- most visibly, restoring focus onto whichever card was last played, on a
    // genuine return from Player, can require scrolling well past the top shelf; an animated
    // glide there reads as an unprompted scroll, since the person pressed BACK, not an arrow key,
    // to get here. TvCarousel re-provides its own (animated, centered) spec for each shelf's
    // *horizontal* scroll, so that per-card browsing motion is unaffected.
    CompositionLocalProvider(LocalBringIntoViewSpec provides InstantBringIntoViewSpec) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(top = SAFE_ZONE_VERTICAL)
                    .focusRequester(placeholderFocusRequester)
                    // Abandons a still-pending placeholder claim the moment focus actually leaves
                    // this exact node for any reason -- browsing into an already-loaded shelf
                    // while Top Picks is still loading, or BACK moving focus out to the rail -- by
                    // marking the trigger consumed right here instead of waiting for the
                    // select-effect above to do it. Without this, Top Picks finishing its load
                    // later (its own delay is random and independent of the other shelves) would
                    // otherwise steal focus back into content out from under wherever the user has
                    // since navigated, undoing their action.
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && placeholderClaimPending) {
                            placeholderClaimPending = false
                            consumedFocusTrigger = firstVideoFocusTrigger
                        }
                    }.focusable()
                    // BACK while browsing opens the drawer with Home's own item focused, instead
                    // of the platform default (which -- since Home has nothing behind it on the
                    // back stack -- would otherwise exit the app). Attached here, on an ancestor of
                    // every shelf's cards, rather than as a BackHandler: NavHost installs its own
                    // internal back handling that, in this app's setup, always wins a BackHandler
                    // priority race regardless of where either one sits in the composition,
                    // silently swallowing BACK before ours ever sees it. Consuming the raw key
                    // event here instead pre-empts that entirely, and naturally only fires while
                    // focus is actually inside this content (once a rail item has focus instead,
                    // this modifier is no longer an ancestor of the focused node, so it's simply
                    // not part of the key event's path at all).
                    .onPreviewKeyEvent { keyEvent ->
                        val isBackKeyDown = keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back
                        if (isBackKeyDown && railFocusRequester != null) {
                            railFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SHELF_SPACING),
        ) {
            // Either kind of restore -- a genuine return from Player, or re-selecting Home from the
            // rail while it's already active -- shares this one key/requester pair; only one of the
            // two can be pending at a time, so which side of the "?:" wins is never ambiguous.
            val restoreFocusItemKey =
                lastPlayedVideoId.takeIf { canRestoreLastPlayed }
                    ?: lastFocusedVideoId.takeIf { canRestoreLastFocused }
            HomeShelf(
                title = TOP_PICKS_TITLE,
                state = uiState.topPicks,
                onResultClick = trackedOnResultClick,
                firstItemFocusRequester = firstVideoFocusRequester,
                focusFirstItemTrigger = firstVideoFocusTrigger,
                restoreFocusItemKey = restoreFocusItemKey,
                restoreFocusRequester = restoreFocusRequester,
                railFocusRequester = railFocusRequester,
                onItemFocused = trackedOnItemFocused,
                header =
                    sessionJoinUrl?.let { joinUrl ->
                        {
                            JoinPartyCard(
                                joinUrl = joinUrl,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = SAFE_ZONE_HORIZONTAL)
                                        .padding(bottom = SHELF_SPACING),
                            )
                        }
                    },
            )
            HomeShelf(
                title = POP_TITLE,
                state = uiState.pop,
                onResultClick = trackedOnResultClick,
                restoreFocusItemKey = restoreFocusItemKey,
                restoreFocusRequester = restoreFocusRequester,
                railFocusRequester = railFocusRequester,
                onItemFocused = trackedOnItemFocused,
            )
            HomeShelf(
                title = ROCK_TITLE,
                state = uiState.rock,
                onResultClick = trackedOnResultClick,
                restoreFocusItemKey = restoreFocusItemKey,
                restoreFocusRequester = restoreFocusRequester,
                railFocusRequester = railFocusRequester,
                onItemFocused = trackedOnItemFocused,
            )
            Spacer(modifier = Modifier.height(BOTTOM_SPACER_HEIGHT))
        }
    }
}

/**
 * A titled, horizontally-scrollable row of video cards -- the "Standard Card" pattern from the TV
 * cards guidelines (developer.android.com/design/ui/tv/guides/components/cards), reusing the same
 * [FocusableCard] as the search results grid. The scrolling/focus-management engine itself lives
 * in [TvCarousel]; this composable owns only the title and Loading/Error/Loaded state handling.
 */
@Composable
private fun HomeShelf(
    title: String,
    state: ShelfUiState,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    focusFirstItemTrigger: Int = 0,
    restoreFocusItemKey: String? = null,
    restoreFocusRequester: FocusRequester? = null,
    railFocusRequester: FocusRequester? = null,
    onItemFocused: ((Any) -> Unit)? = null,
    // Rendered above the title, *inside* the shelf's own bring-into-view area -- so the join
    // banner above Top Picks scrolls back into view along with it (rather than staying scrolled
    // off, since the banner itself is never focused and nothing else would ever reveal it again).
    header: (@Composable () -> Unit)? = null,
) {
    // Requests the whole shelf (title included) into view -- not just the focused card -- when
    // any card in this shelf gains focus, so scrolling back up to an earlier shelf always reveals
    // its title again rather than stopping as soon as the card itself is visible.
    val shelfBringIntoViewRequester = remember { BringIntoViewRequester() }
    var shelfHasFocus by remember { mutableStateOf(false) }
    // Keyed on shelfHasFocus rather than launched ad hoc from onFocusChanged via
    // rememberCoroutineScope(): a plain scope.launch{} there would start a new, uncancelled
    // bringIntoView() coroutine on every focus change within the shelf (moving between its own
    // cards re-fires onFocusChanged with hasFocus already true -> true, but a *loss* of focus
    // followed by regaining it would stack jobs). Keying a LaunchedEffect on the boolean instead
    // means Compose itself cancels any still-running call before starting the next one.
    LaunchedEffect(shelfHasFocus) {
        if (shelfHasFocus) shelfBringIntoViewRequester.bringIntoView()
    }

    val density = LocalDensity.current
    val prefetchSizePx =
        remember(density) {
            with(density) {
                val widthPx = SHELF_CARD_WIDTH.roundToPx()
                IntSize(widthPx, (widthPx / THUMBNAIL_ASPECT_RATIO).toInt())
            }
        }

    Column(
        modifier =
            Modifier
                .bringIntoViewRequester(shelfBringIntoViewRequester)
                .onFocusChanged { shelfHasFocus = it.hasFocus },
    ) {
        header?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = SAFE_ZONE_HORIZONTAL),
        )
        Spacer(modifier = Modifier.height(SHELF_TITLE_SPACING))
        when (state) {
            is ShelfUiState.Loading -> LoadingIndicator(modifier = Modifier.fillMaxWidth().height(SHELF_LOADING_HEIGHT))
            is ShelfUiState.Error ->
                Text(
                    text = "Couldn't load \"$title\". Check your connection and try again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(SHELF_LOADING_HEIGHT)
                            .padding(horizontal = SAFE_ZONE_HORIZONTAL),
                )
            is ShelfUiState.Loaded ->
                TvCarousel(
                    items = state.items,
                    key = { it.videoId },
                    firstItemFocusRequester = firstItemFocusRequester,
                    focusFirstItemTrigger = focusFirstItemTrigger,
                    leftEdgeFocusRequester = railFocusRequester,
                    restoreFocusItemKey = restoreFocusItemKey,
                    restoreFocusRequester = restoreFocusRequester,
                    onItemFocused = onItemFocused,
                    imagePrefetch =
                        TvCarouselImagePrefetch(
                            thumbnailUrl = { it.thumbnailUrl },
                            sizePx = prefetchSizePx,
                        ),
                ) { index, item, itemModifier ->
                    FocusableCard(
                        title = formatVideoTitle(item.title),
                        subtitle = null,
                        thumbnailUrl = item.thumbnailUrl,
                        durationSeconds = item.durationSeconds,
                        onClick = { onResultClick(state.items, index) },
                        modifier = Modifier.width(SHELF_CARD_WIDTH).then(itemModifier),
                    )
                }
        }
    }
}
