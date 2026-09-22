package com.karalo.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.karalo.core.ui.focus.CenteredBringIntoViewSpec
import com.karalo.core.ui.focus.InstantCenteredBringIntoViewSpec
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/** Shared defaults for [TvCarousel], matching the spacing every row in this app already used. */
object TvCarouselDefaults {
    val ContentPadding = PaddingValues(horizontal = 58.dp, vertical = 20.dp)
    val ItemGutter = 20.dp
    const val PREFETCH_AHEAD_COUNT = 4

    // Compose's own default LazyList prefetching only precomposes exactly one item past the
    // visible window per scroll step -- fine on a fast host, but on this app's real (2GB RAM)
    // reference TV, composing a fresh card can't keep up with a real remote's sustained
    // hold-to-repeat rate. Once that one-item buffer is exhausted, arrow-key focus search (which
    // TvCarousel leaves entirely to Compose's default handling -- see its own doc) has no further
    // composed neighbor to move onto, so a held direction key visibly stops advancing partway
    // through a row instead of continuing to the end.
    //
    // A [LazyLayoutCacheWindow] fixes this the same way, structurally, but is still a *bounded*
    // window (aheadFraction x viewport, refilled at mount and incrementally as real scroll deltas
    // arrive -- confirmed by reading Compose Foundation's CacheWindowLogic source) -- so it raises
    // how much sustained hold-to-repeat this can absorb before running out, not an unconditional
    // guarantee for an arbitrarily long hold. Empirically, 1x viewport ahead reproducibly ran out
    // partway through a real hold-to-repeat run on this app's reference TV; 3x was chosen as a
    // meaningfully larger buffer without prefetching so much of a long row at once that it trades
    // this problem for extra concurrent image-decode contention on the same constrained device.
    const val CACHE_WINDOW_AHEAD_FRACTION = 3f
    const val CACHE_WINDOW_BEHIND_FRACTION = 0.5f
}

/**
 * Opt-in adjacent-item thumbnail prefetching for a [TvCarousel] of [T]. [sizePx] is a best-effort
 * target decode size for the *prefetch* request only -- it has no real layout pass to size against,
 * unlike the row's real on-screen `AsyncImage`s. Paired with [Precision.INEXACT] on both the
 * prefetch and the real image request, so a slightly-off guess here still produces a cache hit
 * later (see `TvCarousel.md`).
 */
@Immutable
data class TvCarouselImagePrefetch<T>(
    val thumbnailUrl: (T) -> String?,
    val sizePx: IntSize,
    val aheadCount: Int = TvCarouselDefaults.PREFETCH_AHEAD_COUNT,
)

/**
 * A reusable, focus-managed, lazily-composed horizontal row of TV content -- the shared engine
 * behind every "browse a row of cards" screen in this app (Home's shelves, Search's results,
 * Search's suggestion chips). Built mostly on standard Compose Foundation/UI mechanisms -- [LazyRow]
 * (lazy composition, with an explicit [LazyLayoutCacheWindow] widening how far ahead of the visible
 * window gets precomposed -- see [TvCarouselDefaults]'s own doc for why the default one-item-ahead
 * behavior isn't enough here), the existing [CenteredBringIntoViewSpec] (YouTube-on-Google-TV-style
 * centered scroll), and [Modifier.focusRestorer] (remembers and restores whichever item last had
 * focus when this row is re-entered) -- *except* for held-repeat LEFT/RIGHT, which this row
 * intercepts and drives explicitly (see the `repeatCount > 0` handling in its own
 * `onPreviewKeyEvent` below) rather than leaving to Compose's default arrow-key focus search.
 *
 * That one exception exists because the default mechanism was proven insufficient on real hardware,
 * not assumed to be: on this app's real (2GB RAM) reference TV, a sustained hold-to-repeat run
 * eventually stalls under the default mechanism, confirmed by frame-by-frame review of a screen
 * recording, and confirmed (by temporarily disabling first the row's/card's animations, then real
 * thumbnail images entirely) to be unrelated to animation or image-decode cost -- it's that Compose's
 * default arrow-key focus search needs each next item already composed to move onto it, and a real
 * remote's key-repeat rate can arrive faster than that composition (even of a wider cache window's
 * worth of upcoming items) can keep up on this hardware. A single press (`repeatCount == 0`) is
 * *not* intercepted -- it's left on the default path unchanged, since that case was never observed
 * to be broken (independent of the animation-sequencing fix elsewhere in this file/`FocusableCard`).
 * For a held repeat, this row instead coalesces a burst of repeat events into "jump to wherever the
 * input has gotten to" via [LazyListState.scrollToItem] (forcing that target's composition
 * synchronously, sidestepping the prefetch race entirely) + an explicit per-index [FocusRequester],
 * with `collectLatest` ensuring an in-flight jump is superseded by a newer one rather than queued.
 * See `TvCarousel.md` alongside this file for the full rationale and known limitations.
 *
 * [itemContent] is a slot, not a baked-in card, so a text-chip row (no thumbnails) and a
 * video-card row (via [FocusableCard]) can share this same engine.
 *
 * @param key stable identity per item, passed straight to the underlying `LazyRow`'s `itemsIndexed`
 *   -- lets Compose diff/reuse composed items correctly instead of relying on positional identity.
 * @param firstItemFocusRequester attached to item 0; required for [focusFirstItemTrigger]/
 *   [focusFirstItemOnDownTrigger] to have anywhere to send focus, and used as [focusRestorer]'s
 *   fallback target when nothing has been focused in this row yet.
 * @param focusFirstItemTrigger a fresh (larger than any previously-seen) value snaps focus back to
 *   item 0 -- e.g. an explicit rail reselect while this row is already showing, scrolled well past
 *   its start. Tracked via `rememberSaveable` so a plain remount for an unrelated reason (e.g. a
 *   genuine return from the player) is never mistaken for a fresh trigger.
 * @param focusFirstItemOnDownTrigger the same jump-to-item-0 behavior, but for a purely
 *   same-session keypress echo (e.g. DOWN from a search field above this row) that can never go
 *   stale across an external remount -- so no `rememberSaveable` bookkeeping is needed here.
 * @param upFocusRequester if set, UP from any item in this row deterministically requests focus
 *   here instead of leaving it to Compose's default (ambiguous) spatial focus search.
 * @param leftEdgeFocusRequester if set, LEFT on item 0 only deterministically requests focus here
 *   (e.g. a nav rail), for the same reason.
 * @param restoreFocusItemKey when non-null (and different from the previous value), explicitly
 *   scrolls to and focuses whichever item's [key] matches this, via [restoreFocusRequester].
 *   [focusRestorer] alone is *not* sufficient for this: its restore only walks the currently-
 *   composed focus targets, so it silently falls back to the first item when the previously-
 *   focused one is far enough outside the row's freshly-recomposed initial window to not be
 *   composed yet (confirmed empirically -- see `TvCarousel.md`'s known limitations) -- e.g.
 *   returning from a full-screen player after scrolling deep into a shelf, where Navigation-
 *   Compose disposes and recreates this row's whole composition. The caller is responsible for
 *   only ever passing a non-null value when a *genuine* restore should happen (e.g. a real return
 *   from the player, not a mere rail focus-preview remount that looks identical from this row's
 *   own point of view) -- see `TvCarousel.md` for the exact pattern.
 * @param restoreFocusRequester required (and attached to the matching item) whenever
 *   [restoreFocusItemKey] is non-null.
 * @param onItemFocused reports an item's [key] the moment it gains focus -- e.g. so a caller can
 *   track "whichever card the user was last on" as an ordinary [restoreFocusItemKey] target of its
 *   own later, the same way this row already restores onto a *played* item, since native
 *   [focusRestorer] is not reliable enough to do that restoration on its own (see this function's
 *   own doc above).
 * @param imagePrefetch opt-in adjacent-item thumbnail warming; omit for non-thumbnail rows (e.g.
 *   text chips).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvCarousel(
    items: List<T>,
    key: (item: T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = TvCarouselDefaults.ContentPadding,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(TvCarouselDefaults.ItemGutter),
    firstItemFocusRequester: FocusRequester? = null,
    focusFirstItemTrigger: Int = 0,
    focusFirstItemOnDownTrigger: Int = 0,
    upFocusRequester: FocusRequester? = null,
    leftEdgeFocusRequester: FocusRequester? = null,
    restoreFocusItemKey: Any? = null,
    restoreFocusRequester: FocusRequester? = null,
    onItemFocused: ((itemKey: Any) -> Unit)? = null,
    imagePrefetch: TvCarouselImagePrefetch<T>? = null,
    itemContent: @Composable (index: Int, item: T, itemModifier: Modifier) -> Unit,
) {
    if (items.isEmpty()) return
    val listState =
        rememberLazyListState(
            cacheWindow =
                remember {
                    LazyLayoutCacheWindow(
                        aheadFraction = TvCarouselDefaults.CACHE_WINDOW_AHEAD_FRACTION,
                        behindFraction = TvCarouselDefaults.CACHE_WINDOW_BEHIND_FRACTION,
                    )
                },
        )

    // Backing state for the held-repeat LEFT/RIGHT handling -- see this function's own doc for why
    // it exists. focusRequesters is a plain (non-Compose-state) side table: every item registers its
    // own FocusRequester into it on composition, purely so a held repeat can jump straight to and
    // focus an arbitrary index once scrollToItem has forced it into composition, without needing
    // Compose's own arrow-key focus search to discover it. currentFocusedIndex is the one piece that
    // does need to be observable state, since the key handler below reads its latest value.
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    var currentFocusedIndex by remember { mutableIntStateOf(0) }
    var pendingRepeatTargetIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(listState, focusRequesters) {
        snapshotFlow { pendingRepeatTargetIndex }
            .filterNotNull()
            .collectLatest { targetIndex ->
                // Cancelled (not queued) by collectLatest if a newer target arrives first -- this is
                // exactly the coalescing a burst of repeat events needs: chase only the latest
                // requested index, drop stale intermediate ones, rather than visiting every index a
                // fast repeat stream passed through.
                //
                // scrollToItem's own default (scrollOffset = 0) lands the target at the viewport's
                // *start* edge, not centered -- visually confirmed on a real TV to look like the
                // focused card staying pinned to one side for the whole hold, then visibly snapping
                // to center only once focus's own automatic bring-into-view correction finally runs
                // (a real two-stage motion, not merely a missing animation). Passing the same
                // centered-leading-edge offset CenteredBringIntoViewSpec itself would compute lands
                // this jump already centered, so that automatic correction finds nothing left to do.
                listState.scrollToItem(targetIndex, scrollOffset = tvCarouselCenteredScrollOffset(listState))
                focusRequesters[targetIndex]?.requestFocus()
            }
    }
    val repeatJump =
        remember(focusRequesters) {
            TvCarouselRepeatJump(
                focusRequesters = focusRequesters,
                onFocused = { index -> currentFocusedIndex = index },
            )
        }
    val restoreFocusTarget =
        remember(restoreFocusItemKey, restoreFocusRequester) {
            TvCarouselRestoreFocusTarget(itemKey = restoreFocusItemKey, focusRequester = restoreFocusRequester)
        }

    if (firstItemFocusRequester != null) {
        TvCarouselFirstItemEffects(
            listState = listState,
            firstItemFocusRequester = firstItemFocusRequester,
            focusFirstItemTrigger = focusFirstItemTrigger,
            focusFirstItemOnDownTrigger = focusFirstItemOnDownTrigger,
        )
    }

    if (restoreFocusItemKey != null && restoreFocusRequester != null) {
        TvCarouselRestoreFocusEffect(
            listState = listState,
            items = items,
            key = key,
            restoreFocusItemKey = restoreFocusItemKey,
            restoreFocusRequester = restoreFocusRequester,
        )
    }

    if (imagePrefetch != null) {
        TvCarouselImagePrefetchEffect(listState = listState, items = items, prefetch = imagePrefetch)
    }

    // focusRestorer (below) remembers whichever item last had focus when this row is re-entered
    // (e.g. arrowing UP to another row and back DOWN, or a rail focus-preview round trip) and
    // restores it, falling back to the first item otherwise.
    //
    // Both behaviors are switched off for the one recomposition where a [restoreFocusItemKey]
    // restore is pending, though: focusRestorer's onEnter also fires for that restore's explicit
    // requestFocus() onto a specific descendant, and cancels it in favor of its own pick. With
    // nothing remembered yet, that pick was the fallback (item 0). Otherwise it was whichever item
    // focus last *exited* the row from, which survives the Player round trip (it's persisted via
    // SaveableStateRegistry) but isn't updated by focus leaving for Player itself. Confirmed on a
    // real TV: play the 4th card, return, arrow RIGHT twice, play that one, and BACK landed on the
    // 4th card again instead. Overriding onEnter with a no-op here, applied after focusRestorer's
    // own (focus properties apply from the target outward, so the outer one wins), lets the
    // explicit requestFocus() land exactly where it asked to.
    val isRestorePending = restoreFocusItemKey != null

    // Same centering math as CenteredBringIntoViewSpec, but instant rather than animated, for the
    // one recomposition a [restoreFocusItemKey] restore is pending: that restore already scrolls
    // its target as close to centered as it can compute up front (see TvCarouselRestoreFocusEffect),
    // but Compose's own automatic focus-follow correction still runs on top regardless, and in
    // practice still finds a small residual distance to close -- animating *that* still reads as
    // an unprompted scroll, since the person pressed BACK, not an arrow key, to get here.
    val bringIntoViewSpec =
        if (restoreFocusItemKey != null) InstantCenteredBringIntoViewSpec else CenteredBringIntoViewSpec

    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        LazyRow(
            state = listState,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            modifier =
                modifier
                    .focusGroup()
                    .focusProperties { if (isRestorePending) onEnter = {} }
                    .focusRestorer(fallback = firstItemFocusRequester ?: FocusRequester.Default)
                    .then(
                        if (upFocusRequester != null) {
                            Modifier.onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                    upFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                        } else {
                            Modifier
                        },
                    ).onPreviewKeyEvent { keyEvent ->
                        val target =
                            tvCarouselRepeatTargetIndex(
                                keyEvent = keyEvent,
                                currentFocusedIndex = currentFocusedIndex,
                                lastIndex = items.lastIndex,
                            ) ?: return@onPreviewKeyEvent false
                        if (target != currentFocusedIndex) {
                            pendingRepeatTargetIndex = target
                        }
                        true
                    },
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                val itemModifier =
                    tvCarouselItemModifier(
                        index = index,
                        itemKey = key(item),
                        firstItemFocusRequester = firstItemFocusRequester,
                        leftEdgeFocusRequester = leftEdgeFocusRequester,
                        restoreFocusTarget = restoreFocusTarget,
                        repeatJump = repeatJump,
                        onItemFocused = onItemFocused,
                    )
                itemContent(index, item, itemModifier)
            }
        }
    }
}

/**
 * Bundles the two pieces every item needs to support held-repeat LEFT/RIGHT jumping straight to an
 * arbitrary index (see [TvCarousel]'s own doc for why) -- purely to keep [tvCarouselItemModifier]'s
 * own parameter count down, not a meaningful grouping otherwise.
 */
private class TvCarouselRepeatJump(
    val focusRequesters: MutableMap<Int, FocusRequester>,
    val onFocused: (index: Int) -> Unit,
)

/**
 * [restoreFocusItemKey] and [restoreFocusRequester] are always used together (see [TvCarousel]'s own
 * doc on both) -- bundled purely to keep [tvCarouselItemModifier]'s own parameter count down.
 */
private class TvCarouselRestoreFocusTarget(
    val itemKey: Any?,
    val focusRequester: FocusRequester?,
)

/**
 * The per-item focus-related modifiers, kept out of [TvCarousel] itself purely to keep that
 * function's own complexity down.
 */
private fun tvCarouselItemModifier(
    index: Int,
    itemKey: Any,
    firstItemFocusRequester: FocusRequester?,
    leftEdgeFocusRequester: FocusRequester?,
    restoreFocusTarget: TvCarouselRestoreFocusTarget,
    repeatJump: TvCarouselRepeatJump,
    onItemFocused: ((itemKey: Any) -> Unit)?,
): Modifier {
    // Every item (not just the specially-targeted ones below) registers its own FocusRequester and
    // reports when it gains focus -- both purely to support held-repeat LEFT/RIGHT jumping straight
    // to an arbitrary index. Cheap: a FocusRequester is a small holder with no per-frame cost of its
    // own, and onFocusChanged only fires on actual focus transitions, not every recomposition.
    var itemModifier: Modifier =
        Modifier
            .focusRequester(repeatJump.focusRequesters.getOrPut(index) { FocusRequester() })
            .onFocusChanged {
                if (it.isFocused) {
                    repeatJump.onFocused(index)
                    onItemFocused?.invoke(itemKey)
                }
            }
    if (index == 0 && firstItemFocusRequester != null) {
        itemModifier = itemModifier.focusRequester(firstItemFocusRequester)
    }
    if (index == 0 && leftEdgeFocusRequester != null) {
        itemModifier =
            itemModifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                    leftEdgeFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
    }
    val restoreFocusRequester = restoreFocusTarget.focusRequester
    if (restoreFocusRequester != null && itemKey == restoreFocusTarget.itemKey) {
        itemModifier = itemModifier.focusRequester(restoreFocusRequester)
    }
    return itemModifier
}

/**
 * The `scrollToItem` `scrollOffset` that lands a target item's leading edge at the same centered
 * position [CenteredBringIntoViewSpec] itself would compute -- mirroring its own
 * `centeredTargetForLeadingEdge = (containerSize - size) / 2f` math, but negated:
 * `scrollToItem`'s `scrollOffset` is defined as how far *forward* (i.e. further offscreen) the item
 * should be relative to sitting exactly at the viewport's start, the opposite sense of "how far
 * into the viewport its leading edge should sit." [LazyListLayoutInfo.viewportSize] and a currently
 * visible item's size (this app's rows are always uniform-width) are the same two inputs
 * `ContentInViewNode` itself feeds into that formula. Naturally still clamps correctly at either
 * end of the list -- a scrollable can never actually scroll past its real content bounds, matching
 * [CenteredBringIntoViewSpec]'s own "pinned at start/end" doc.
 */
private fun tvCarouselCenteredScrollOffset(listState: LazyListState): Int {
    val layoutInfo = listState.layoutInfo
    val viewportSize = layoutInfo.viewportSize.width
    val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return 0
    return -((viewportSize - itemSize) / 2)
}

/**
 * For a held (`repeatCount > 0`) DirectionRight/DirectionLeft key event, the next index this row
 * should jump to -- or null if this event isn't one this row's held-repeat handling should act on:
 * a key-up, a different key, a single (non-repeat) press (left entirely to Compose's default
 * arrow-key focus search -- see [TvCarousel]'s own doc for why that split is deliberate), or a LEFT
 * repeat while already at index 0 (left for the existing per-item [leftEdgeFocusRequester] escape
 * hatch to handle instead, exactly as a single LEFT press there already does).
 */
private fun tvCarouselRepeatTargetIndex(
    keyEvent: KeyEvent,
    currentFocusedIndex: Int,
    lastIndex: Int,
): Int? {
    val isRepeatKeyDown =
        keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount > 0
    val delta =
        when {
            !isRepeatKeyDown -> null
            keyEvent.key == Key.DirectionRight -> 1
            keyEvent.key == Key.DirectionLeft -> -1
            else -> null
        } ?: return null
    if (delta < 0 && currentFocusedIndex == 0) return null
    return (currentFocusedIndex + delta).coerceIn(0, lastIndex)
}

/**
 * The two explicit "jump to the first item" effects, kept out of [TvCarousel] itself purely to
 * keep that function's own complexity down -- see [focusFirstItemTrigger]'s and
 * [focusFirstItemOnDownTrigger]'s own doc on [TvCarousel] for what each one is for.
 */
@Composable
private fun TvCarouselFirstItemEffects(
    listState: LazyListState,
    firstItemFocusRequester: FocusRequester,
    focusFirstItemTrigger: Int,
    focusFirstItemOnDownTrigger: Int,
) {
    var consumedFocusFirstItemTrigger by rememberSaveable { mutableIntStateOf(0) }
    if (focusFirstItemTrigger > consumedFocusFirstItemTrigger) {
        LaunchedEffect(focusFirstItemTrigger) {
            consumedFocusFirstItemTrigger = focusFirstItemTrigger
            listState.scrollToItem(0)
            firstItemFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(focusFirstItemOnDownTrigger) {
        if (focusFirstItemOnDownTrigger > 0) {
            listState.scrollToItem(0)
            firstItemFocusRequester.requestFocus()
        }
    }
}

/**
 * Explicitly scrolls to and focuses whichever item's key matches [restoreFocusItemKey] -- see
 * [TvCarousel]'s own doc on that parameter for why [focusRestorer] alone isn't enough for this
 * case. Re-resolves the target index by key on every [items]/[restoreFocusItemKey] change rather
 * than once, since the matching item's position can differ across recompositions of a freshly-
 * fetched list.
 *
 * Passes the same centered [tvCarouselCenteredScrollOffset] the held-repeat jump above uses, for
 * the exact same reason (see its own comment): `scrollToItem`'s default `scrollOffset = 0` lands
 * the target at the viewport's *start* edge, not centered, so the subsequent `requestFocus()`
 * below still triggers a second, separately-animated correction from that edge to the centered
 * position `CenteredBringIntoViewSpec` computes -- invisible for the first couple of items only
 * because their centered position is clamped to the same start edge, but a real, visible glide
 * for anything deeper into the row (e.g. restoring onto the 3rd or 4th card after returning from
 * Player). Landing already centered here leaves that correction nothing to do.
 */
@Composable
private fun <T> TvCarouselRestoreFocusEffect(
    listState: LazyListState,
    items: List<T>,
    key: (item: T) -> Any,
    restoreFocusItemKey: Any,
    restoreFocusRequester: FocusRequester,
) {
    val restoreTargetIndex = items.indexOfFirst { key(it) == restoreFocusItemKey }
    LaunchedEffect(restoreFocusItemKey, restoreTargetIndex) {
        if (restoreTargetIndex >= 0) {
            listState.scrollToItem(restoreTargetIndex, scrollOffset = tvCarouselCenteredScrollOffset(listState))
            restoreFocusRequester.requestFocus()
        }
    }
}

/**
 * Warms Coil's shared image cache for the next [TvCarouselImagePrefetch.aheadCount] items past
 * whatever's currently visible, so their thumbnails are likely already decoded and cached by the
 * time they'd actually scroll into view and gain focus. Coil 2.x has no dedicated preload API --
 * `enqueue` with no `target` set is the documented fire-and-forget pattern: the request still runs
 * the full fetch-decode-cache pipeline, it just has nothing to bind the result to.
 *
 * [snapshotFlow] + [distinctUntilChanged] on the last visible index is the standard, cheap way to
 * react to [LazyListState.layoutInfo] changes without polling every frame -- it only re-evaluates
 * when the visible window actually shifts (once per scroll step), not once per frame.
 */
@Composable
private fun <T> TvCarouselImagePrefetchEffect(
    listState: LazyListState,
    items: List<T>,
    prefetch: TvCarouselImagePrefetch<T>,
) {
    val context = LocalContext.current
    LaunchedEffect(listState, items, prefetch) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        }.distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex < 0) return@collect
                val prefetchRange = (lastVisibleIndex + 1)..(lastVisibleIndex + prefetch.aheadCount)
                prefetchRange
                    .asSequence()
                    .mapNotNull { items.getOrNull(it) }
                    .forEach { item ->
                        val url = prefetch.thumbnailUrl(item) ?: return@forEach
                        context.imageLoader.enqueue(
                            ImageRequest
                                .Builder(context)
                                .data(url)
                                .size(prefetch.sizePx.width, prefetch.sizePx.height)
                                .precision(Precision.INEXACT)
                                .build(),
                        )
                    }
            }
    }
}
