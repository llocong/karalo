# ADR-0001: Jetpack Compose for TV over the Leanback library

**Status:** Accepted

## Context

Android TV apps have historically been built on the `leanback` library (View-based
`BrowseFragment`/`PlaybackFragment` etc.). Google now recommends Compose for TV
(`androidx.tv:tv-material`, `androidx.tv:tv-foundation`) for new apps, but Leanback remains more
battle-tested and has more Stack Overflow-era examples.

## Decision

Use Compose for TV, not Leanback, for the entire UI.

- v1's UI is genuinely simple (a nav rail, a search field + results grid, a player with 4
  controls) — none of Leanback's heavier scaffolding (`BrowseSupportFragment` rows/headers,
  Leanback's opinionated search fragment) buys us anything here.
- Compose for TV shares the same mental model, theming, and tooling as the rest of the Android
  Compose ecosystem — no Fragment/View interop layer to maintain, and it's straightforward to
  reuse patterns from `androidx.compose.foundation`/`material3` knowledge.
- Google's own guidance and new sample apps (JetStream, etc.) are Compose-for-TV-first going
  forward.

## Consequences

- `androidx.tv:tv-material` is younger and less battle-tested than Leanback — expect some API
  churn on library upgrades (pin the exact version in `gradle/libs.versions.toml`).
- We lose Leanback's built-in `SearchOrbView`/voice-search affordances and its default
  row-browsing patterns, but v1 doesn't need them.
- `androidx.tv:tv-foundation` turned out to have no Lazy grid/list components in its 1.0.0 stable
  release (they were dropped before stabilization) — the results grid uses mainline
  `androidx.compose.foundation.lazy.grid.LazyVerticalGrid` plus `Modifier.focusGroup()`
  (`androidx.compose.foundation`) and `Modifier.focusRestorer()` (`androidx.compose.ui.focus`)
  for D-pad traversal/scroll-into-view/focus restoration, not a TV-specific grid. `tv-foundation`
  itself is unused and not a dependency of this project — only `tv-material` (buttons, cards,
  theming, nav list items) is.
