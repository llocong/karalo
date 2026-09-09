# ADR-0004: Media3 (ExoPlayer) for playback

**Status:** Accepted

## Context

Karalo needs to play direct video URLs and DASH/HLS manifests resolved by `:youtube-client`
(see ADR-0002), with a fully custom control UI and correct handling of TV remote hardware media
keys (play/pause, next/previous).

## Decision

Use `androidx.media3` (the successor to ExoPlayer2/`com.google.android.exoplayer2`) —
`media3-exoplayer` + `media3-exoplayer-dash` + `media3-exoplayer-hls` + `media3-ui`.

- It's Google's actively maintained playback stack (ExoPlayer proper is in maintenance mode as of
  Media3's stabilization) and the de facto standard for third-party Android video apps, including
  SmartTube.
- `DefaultMediaSourceFactory` auto-selects DASH/HLS/progressive handling based on the resolved
  stream's MIME type, so `:feature-player`'s `PlaybackRepositoryImpl` doesn't need to hand-pick a
  `MediaSource` implementation — see `ExoPlayerModule`.
- We use `PlayerView` only as a bare surface (`useController = false`) and build our own
  Play/Pause/Previous/Next/progress overlay in Compose (`PlayerControlsOverlay`), so Media3's
  default (much richer) control UI is intentionally unused.

## Consequences

- No `MediaSession`/notification integration in v1 (see README "Known limitations") — hardware
  media keys are routed manually via `MainActivity.dispatchKeyEvent` →
  `com.karalo.core.common.mediakeys.MediaKeyRouter` → the active `PlayerViewModel`, rather than
  through Media3's `MediaSession`, which would give this "for free" along with lock-screen
  controls and better Assistant integration. Revisit if that's ever needed.
- Stream URLs resolved from unofficial extraction can be short-lived or need specific request
  headers; `PlayerViewModel`/`ResolveStreamUseCase` should be extended to re-resolve on a
  `PlaybackException` (e.g. HTTP 403) rather than assume a resolved URL stays valid all session —
  not yet implemented in v1, flagged as a known gap.
