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

- `androidx.tv:tv-material`/`tv-foundation` are younger and less battle-tested than Leanback —
  expect some API churn on library upgrades (pin exact versions in `gradle/libs.versions.toml`).
- We lose Leanback's built-in `SearchOrbView`/voice-search affordances and its default
  row-browsing patterns, but v1 doesn't need them.
- D-pad focus behavior (traversal, scroll-into-view, focus restoration) is handled via
  `tv-foundation`'s `TvLazyVerticalGrid` and `Modifier.focusRestorer()` rather than Leanback's
  built-in row/grid presenters — verify this on a real device/emulator early (see README "Known
  limitations" and the player/search screens).
