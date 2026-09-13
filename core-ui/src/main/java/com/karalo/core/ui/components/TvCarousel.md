# TvCarousel

A reusable, focus-managed, lazily-composed horizontal row for TV — the shared engine behind
every "browse a row of cards" screen in Karalo (Home's shelves, Search's results row, Search's
suggestion chips). See `TvCarousel.kt` in this same directory for the implementation.

```kotlin
TvCarousel(
    items = videos,
    key = { it.videoId },
    imagePrefetch = TvCarouselImagePrefetch(
        thumbnailUrl = { it.thumbnailUrl },
        sizePx = cardSizePx,
    ),
) { index, item, itemModifier ->
    FocusableCard(
        title = item.title,
        /* ... */
        onClick = { onResultClick(index, item.videoId) },
        modifier = Modifier.width(CARD_WIDTH).then(itemModifier),
    )
}
```

## What this is — and isn't

`TvCarousel` is mostly a thin, opinionated wrapper around standard
`androidx.compose.foundation`/`androidx.compose.ui` APIs: `LazyRow`, `LazyListState` (lazy
composition + an explicit `LazyLayoutCacheWindow`), `Modifier.focusRestorer()`, and this app's
existing `CenteredBringIntoViewSpec` — **except** for held-repeat LEFT/RIGHT, which it does
intercept and drive explicitly (see **Hold-to-repeat handling** below). That's a real, narrow
exception to "no custom engine," not an oversight: the first pass at this component assumed the
standard stack was sufficient for hold-to-repeat too, and that assumption held up on the emulator
but turned out to be **wrong on a real TV remote** — see below for how that was diagnosed and what
actually fixes it. Every other behavior (single-press LEFT/RIGHT, focus-follow centering, UP/DOWN
between shelves, focus restoration) is still handled by the standard stack, unmodified.

Investigating this app's original stutter during hold-to-repeat also found two real, unrelated
bugs along the way, both fixed by using the standard stack *correctly* rather than by adding new
machinery: a genuinely uncancelled coroutine in `HomeShelf`'s old bring-into-view handling, and a
Compose-focus-restoration gap (see **Known limitations**).

## Component choices

- **`LazyRow` over a hand-rolled scroller or `androidx.tv.foundation`.** The latter was checked
  directly this session: it's present in the Gradle cache only as an orphaned transitive
  dependency (not declared anywhere in this repo), has no sources jar, and its actual compiled
  classes contain no `TvLazyRow`/`PivotOffsets`/lazy-list helpers at all — just three unrelated
  classes. `androidx.tv.material3`'s own official samples use plain `LazyRow` + `items`, confirming
  it's the currently-sanctioned pattern, not a TV-specific list type.
- **`CenteredBringIntoViewSpec`** (pre-existing in this codebase) gives the YouTube-on-Google-TV
  centering behavior: the focused item stays centered through the middle of the row, pinned at the
  start/end near either edge. It's a stateless object implementing `BringIntoViewSpec` — pure
  arithmetic, no per-frame cost of its own.
- **`Modifier.focusRestorer()`** remembers whichever item last had focus when the row is re-entered
  (UP/DOWN between Home's shelves, a nav-rail focus-preview round trip) and restores it, falling
  back to the first item otherwise. This is the standard, sanctioned API for exactly this — see
  **Known limitations** below for the one case it does *not* cover on its own.

## D-pad handling

A **single** LEFT/RIGHT press (`KeyEvent.nativeKeyEvent.repeatCount == 0`) is handled entirely by
Compose's default mechanisms — `TvCarousel` does not intercept or consume it. A **held** LEFT/RIGHT
(`repeatCount > 0`) is intercepted and driven explicitly — see **Hold-to-repeat handling** below for
why. The other `onPreviewKeyEvent` overrides are deliberate, narrow escape hatches where Compose's
default *spatial* focus search would otherwise be ambiguous, unrelated to repeat handling:

- `upFocusRequester`: UP from any item in the row → a specific target (e.g. a search field above
  it), instead of Compose guessing based on screen position.
- `leftEdgeFocusRequester`: LEFT on item 0 only → a specific target (e.g. the nav rail). A *held*
  LEFT at item 0 also falls through to this same handler — the repeat interceptor deliberately
  ignores that one case so this escape hatch keeps working exactly as a single LEFT press there
  already does.

Selecting an item (`onClick`) needs no custom key handling either — `Modifier.clickable` (used
inside `FocusableCard`) already treats D-pad center/Enter as a click natively.

## Animation coordination

Real-remote testing surfaced two distinct animation bugs, both now fixed, that are worth
understanding separately from the hold-to-repeat throughput problem below — they were the actual
cause of a *single* press feeling like it stutters, which persisted after the throughput fix and had
to be root-caused independently.

- **The centering-scroll animation and a card's own focus-scale animation used to fight over the
  same bounds.** `FocusableCard`'s scale-up `graphicsLayer` used to sit on the same node as
  `.clickable()` (the actual focus target) — and the row's centering scroll re-reads that node's live
  bounds every frame for the whole scroll (confirmed by reading `ContentInViewNode.launchAnimation()`
  directly). With the scale on that same node, those bounds kept growing for the entire 300ms
  focus-in tween, so the scroll had to keep chasing a moving target: a fast scroll, then a small
  correcting snap right as the scale animation finished settling — read as stutter on every single
  focus change. Fixed by moving the scale onto a plain visual descendant one level in (see
  `FocusableCard.kt`'s own comment on its `Box`/`Column` split) so the focus node's bounds are fixed
  for the whole animation.
- **The row's default scroll animation was too fast to read as motion.** Compose Foundation's
  `BringIntoViewSpec.scrollAnimationSpec` defaults to a fast, no-bounce `spring()` — on a real remote
  that read as an instant jump rather than a glide, which itself presented as stutter (confirmed by
  the user: moving between two already-*visible* cards, where no scroll happens at all, felt smooth
  on its own — only scroll-requiring moves stuttered). Fixed by overriding it in
  `CenteredBringIntoViewSpec` with a 250ms eased `tween`.

A now-**reverted** dead end, left here so it isn't tried again: at one point the scroll and the
scale animation were made to run *sequentially* (`FocusableCard` waiting for the row's scroll to
settle, via a `LocalTvCarouselIsScrolling` composition local, before starting its own scale
animation), on the hypothesis that running both at once was itself the cause of the stutter above.
Once the real cause (the bounds-coupling bug, fixed separately) was root-caused, that sequencing
turned out to be solving a problem that no longer existed — its only remaining effect was a
perceptible, unwanted delay between the scroll finishing and the focus "pop" starting, confirmed by
the user on a real TV in both the single-press and held-repeat cases. Removed entirely: the scale
animation runs concurrently with the row's scroll again (a plain `animateFloatAsState`, exactly as
before any of this investigation started), which is correct now that the two no longer interact.

## Hold-to-repeat handling

The first version of this component assumed the standard stack was enough for held LEFT/RIGHT too:
reading Compose Foundation's `ContentInViewNode`/`BringIntoViewRequestPriorityQueue` source showed
that *overlapping* bring-into-view requests (what a stream of same-direction key-repeat events
produces) are coalesced into one retargeting scroll-animation job rather than queued, and manual
emulator verification (120-item stress row, 100+ rapid-fire presses, `adb shell dumpsys gfxinfo`
showing ~0% janky frames) found nothing pathological. **That assumption turned out to be wrong on a
real remote and a real (2GB RAM) TV box** — a genuinely different environment than either the
emulator or `adb shell input keyevent` bursts, both of which run on far stronger effective hardware
and don't reproduce a real remote's actual sustained key-repeat rate.

**Symptom, confirmed on-device:** holding RIGHT from item 0 always stalled at the same item (~9),
resuming briefly then stalling further out once the cache window (below) was widened, and in one
case froze completely for several seconds (confirmed frame-by-frame from a screen recording — even
the debug overlay's own latency counter stopped updating, not just the row).

**Root cause, isolated by elimination, not assumed:** two rounds of temporarily disabling first all
animation (an instant `snap()` scroll spec, `Animatable.snapTo` instead of `animateTo` for scale),
then real thumbnail images entirely (a static color `Box` instead of `AsyncImage`, prefetch enqueue
skipped too) — both independently confirmed the stall was identical either way, ruling out animation
and image-decode cost as the cause. What's left: Compose's default arrow-key focus search requires
each next item to already be composed to move onto it. A real remote's key-repeat rate can arrive
faster than that composition — even with a wider cache window's worth of upcoming items prefetched
(see `TvCarouselDefaults.CACHE_WINDOW_AHEAD_FRACTION`'s own doc) — can keep up on this hardware. A
side investigation also found a genuine perf bug of its own along the way, in the scroll/scale
animation-sequencing mechanism that existed at the time (see **Animation coordination** above for
why that mechanism was later removed entirely): it ran its full `snapshotFlow`-waiting machinery on
*every* composed card, including cards that had never been focused, and with a wide cache window
composing many cards at once, that alone was enough to fully freeze the row for several seconds
under load. Moot now that the sequencing itself is gone, but the underlying lesson still applies to
any future per-card effect here: gate it on whether the card has actually been focused, not just on
whether it's composed, since the cache window means many more cards are composed than are ever
actually navigated onto.

**The fix:** `TvCarousel` now intercepts LEFT/RIGHT itself, but *only* for a held repeat
(`keyEvent.nativeKeyEvent.repeatCount > 0` — a genuine single press is untouched, left entirely on
the default path that was never observed to be broken). Each accepted repeat event updates a single
piece of state (`pendingRepeatTargetIndex`); a `LaunchedEffect` collects it via
`snapshotFlow { }.collectLatest { }`, so a fast burst of repeat events coalesces into "jump straight
to wherever the input has gotten to" rather than visiting every index in between. Landing there uses
`LazyListState.scrollToItem(index, scrollOffset)`, which **forces that target's composition
synchronously** as part of the call — sidestepping the prefetch race entirely instead of hoping
background composition keeps up — followed by an explicit per-index `FocusRequester` (every item
registers its own into a plain side-table map on composition) to actually land focus there once it
exists. `collectLatest` means an in-flight jump is cancelled outright by a newer one, not queued.

One further real bug surfaced testing this on-device: `scrollToItem`'s own default `scrollOffset`
(`0`) lands the target at the viewport's *start* edge, not centered — visually, the focused card
stayed pinned toward one side for the whole hold, then visibly snapped to center only once Compose's
own separate "bring newly-focused thing into view" correction ran (a real two-stage motion, not a
missing animation, even though it looked similar to one). Fixed by computing the same centered
leading-edge offset `CenteredBringIntoViewSpec` itself would (`tvCarouselCenteredScrollOffset`,
mirroring its `(containerSize - size) / 2f` math with `LazyListLayoutInfo.viewportSize` and a
currently-visible item's size) and passing that directly to `scrollToItem`, so the jump lands
already where the automatic correction would have taken it anyway — nothing left to correct
afterward.

Verified end-to-end on the real reference TV: holding RIGHT/LEFT from either end of the 120-item
stress row now reaches the true opposite end with no stall, and the focused card stays visually
centered throughout the hold instead of snapping at release.

## Image-loading optimization

- **One shared `ImageLoader`** for the whole app (`core-ui/.../image/KaraloImageLoader.kt`,
  installed via `KaraloApplication : ImageLoaderFactory`), with its memory cache capped at 15% of
  available app memory (`MemoryCache.Builder(context).maxSizePercent(0.15)`) — tuned for the
  ~2GB-RAM reference device this app was profiled on.
- **`Precision.INEXACT`** on every request — both `FocusableCard`'s real, layout-sized `AsyncImage`
  request and `TvCarousel`'s prefetch requests. Coil 2.x's `Precision.INEXACT` lets a cached image
  be reused even if it doesn't exactly match the requested size, which is what makes the prefetch
  path's necessarily-approximate size guess (it has no real layout pass to measure against) still
  produce a cache hit on the real request later.
- **Adjacent-item prefetch** (`TvCarouselImagePrefetch`): a `LaunchedEffect` watches
  `LazyListState.layoutInfo`'s last-visible index via `snapshotFlow` + `distinctUntilChanged`, and
  on every shift, fire-and-forget `imageLoader.enqueue(...)`s the next `aheadCount` items past it
  (default 4) — no `.target()` set, so it just warms the fetch→decode→cache pipeline without
  needing anywhere to bind the result. Coil 2.x has no dedicated preload API; this is the documented
  pattern for one.

## Tuning knobs

| Knob | Where | Default |
|---|---|---|
| `TvCarouselImagePrefetch.aheadCount` | per call site | 4 |
| `TvCarouselImagePrefetch.sizePx` | per call site | (caller computes from card width/aspect ratio) |
| Memory cache size | `KaraloImageLoader.kt` `MEMORY_CACHE_SIZE_PERCENT` | 0.15 |
| Focus scale amount/timing | `FocusableCard.kt` `FOCUSED_SCALE`/`FOCUS_SCALE_DURATION_MS`/`UNFOCUS_SCALE_DURATION_MS` | 1.1x / 300ms / 500ms |
| Centering-scroll animation duration/easing | `CenteredBringIntoViewSpec.kt` `SCROLL_ANIMATION_DURATION_MS` (+ `FastOutSlowInEasing`) | 250ms |
| Cache-window ahead/behind size (how far past the visible window gets precomposed) | `TvCarouselDefaults.CACHE_WINDOW_AHEAD_FRACTION`/`CACHE_WINDOW_BEHIND_FRACTION` | 3x / 0.5x viewport |
| `TvCarouselDefaults.ContentPadding`/`ItemGutter` | `TvCarousel.kt` | 58dp horizontal/20dp vertical, 20dp gutter |
| Nested-shelf prefetch depth | not currently used — see below | — |

If Home ever grows enough shelves to need a `LazyColumn` of shelves instead of today's eager
`Column` + `verticalScroll`, `rememberLazyListState(prefetchStrategy = LazyListPrefetchStrategy
(nestedPrefetchItemCount = N))` on the *outer* list lets a not-yet-visible shelf's `TvCarousel`
precompose its first `N` items before it scrolls into view — worth revisiting then, not needed now.

## Known limitations

- **`focusRestorer()` alone does not survive every navigation round trip.** Its restore only walks
  the *currently-composed* focus targets (confirmed by reading its source) — if the previously-
  focused item is scrolled far enough into a row that it's no longer composed by the time the row
  is freshly recreated (e.g. returning from the full-screen Player after scrolling deep into a
  shelf, which disposes and recomposes the whole screen), it silently falls back to the first item
  instead. This was caught empirically by
  `app/src/androidTest/.../TvCarouselFocusRestorationRoundTripTest.kt` — the test initially failed
  with exactly this symptom. `TvCarousel`'s `restoreFocusItemKey`/`restoreFocusRequester` parameters
  are the fix: an explicit, caller-driven "scroll to and focus this specific item" mechanism,
  reinstated from what was originally a bespoke, per-screen "last played video" tracker and
  generalized into the component itself. `HomeScreenContent`/`SearchScreenContent` wire it using the
  same `playerReturnTrigger`-gated pattern as before (only treat it as a genuine restore on an
  actual return from the Player, never on a mere rail focus-preview remount, which looks identical
  from the row's own point of view). Bottom line: **`focusRestorer()` handles the general
  same-composition case (UP/DOWN between shelves, a rail-preview round trip) for free; the explicit
  mechanism is still needed for "resume after a full-screen navigation," and both coexist by
  design.**
- **The held-repeat mechanism (`tvCarouselRepeatTargetIndex`/`tvCarouselCenteredScrollOffset`) has
  no automated test of its own.** Compose UI test's `pressKey` dispatches a single synthetic key
  event with `repeatCount == 0`, so it can't exercise (or regress-guard) the `repeatCount > 0` path
  at all — the existing automated edge/pinning/centering tests all exercise the *default*, non-repeat
  path only. This mechanism's actual correctness was established by root-causing a real, reproducible
  on-device failure (see **Hold-to-repeat handling**) and confirming the fix on the same physical
  device afterward, not by an automated regression test — a real gap, worth a dedicated
  instrumented test (e.g. driving raw `KeyEvent`s with `repeatCount > 0` directly rather than through
  `performKeyInput`) as follow-up work.
- **Real hold-to-repeat *feel* (as opposed to correctness) is still judged manually, not by
  automated tests** — on a real device, against this app's actual rows (Home's shelves, Search's
  results/suggestion chips), for the same reason as above plus the fact that "feels smooth" isn't
  something an automated test asserts. A debug-only stress-test screen (100+ items, a simulated
  slow-network toggle) existed earlier in this component's development specifically to isolate this
  investigation from the real app's own content/data; it's since been removed now that the real app
  itself is the thing being tested against.
- **Final smoothness judgment needs a real TV, not just the emulator.** The emulator runs on the
  host machine's CPU, which is far stronger than a real TV box's — clean emulator numbers rule out
  pathological bugs (and did catch one, during this component's development: an early, buggy debug-
  only recomposition counter created a genuine infinite-recomposition feedback loop by writing to a
  `MutableState` from within the `SideEffect` that read it — fixed by using a plain non-`State`
  counter instead) but aren't a substitute for judging real-TV feel.
