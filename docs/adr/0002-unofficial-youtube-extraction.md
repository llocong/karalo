# ADR-0002: Unofficial YouTube extraction (NewPipeExtractor) over the official Data API

**Status:** Accepted

## Context

Karalo needs to search YouTube and play videos with a fully custom, ad-free, remote-friendly
player (Play/Pause, Previous, Next, progress bar only — no YouTube branding/overlay). Two options
exist:

1. **Official YouTube Data API v3** for search (API-key-gated, quota-limited) + YouTube's
   embeddable/IFrame player for playback. ToS-compliant, but the embeddable player is a
   WebView-based black box: it shows ads, and its remote-control surface is limited to a small
   JS bridge — a fully custom control UI like the one this app requires isn't possible.
2. **Unofficial extraction** (the same approach [SmartTube](https://github.com/yuliskov/smarttube)
   and [NewPipe](https://github.com/TeamNewPipe/NewPipe) use, via
   [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)): parse YouTube's
   internal/web APIs to get search results, suggestions, and direct, ad-free stream URLs that feed
   straight into ExoPlayer under our own UI.

## Decision

Use unofficial extraction via NewPipeExtractor, isolated entirely behind the `:youtube-client`
module's `YouTubeClient` interface (see README "How search & playback work"). No other module
references NewPipeExtractor types directly.

This is the only way to meet the actual product requirement (ad-free playback, fully custom
minimal controls) — the official API cannot deliver that. It's accepted knowingly, not
overlooked.

## Consequences

- **Violates YouTube's Terms of Service.** This is a real, ongoing risk (rate-limiting, account/IP
  blocks) that this app does nothing to mitigate — it's inherent to the approach.
- **No Play Store distribution** while this remains the extraction strategy — Play policy has
  historically rejected/removed apps doing this. Releases ship via GitHub Releases only (see
  `.github/workflows/release.yml`); revisit this decision explicitly (new ADR) before ever
  targeting Play.
- **Ongoing maintenance burden.** YouTube changes its internal APIs without notice; extraction
  that works today can silently start failing (empty results, 403s on stream URLs) until
  NewPipeExtractor — and our pinned version of it — catches up. Pin an exact tag (not a floating
  branch) in `gradle/libs.versions.toml`, and expect to bump it reactively.
- **Isolation pays off later.** Because everything is behind `YouTubeClient`, a future switch
  (official API + accepting the UX trade-off, a different extraction library, a backend proxy)
  only touches `:youtube-client`.
