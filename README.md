# Karalo

A native Android TV / Google TV app for karaoke: search YouTube and sing along, ad-free, with a
minimal remote-friendly player. Every search is silently prefixed with `karaoke ` — type
"Rihanna" and it searches "karaoke Rihanna".

**v1 scope is intentionally small:** a left-nav with Home and Search, YouTube search with
autocomplete, a results grid, and a stripped-down player (Play/Pause, Previous, Next, progress
bar only). No microphone/scoring, no accounts, no monetization.

## Architecture

Clean Architecture + MVVM across the Android app's Gradle modules, plus an independent backend
service (`backend/`, see "Karaoke remote control" below):

```
:app  ──▶ :feature-search, :feature-player, :feature-home, :core-ui, :core-common, :core-karaoke
:feature-search ──▶ :youtube-client, :core-ui, :core-common
:feature-player ──▶ :youtube-client, :core-ui, :core-common, :core-karaoke
:feature-home   ──▶ :core-ui, :core-common
:core-karaoke   ──▶ :core-network, :core-common
:youtube-client ──▶ :core-network, :core-common
:core-network   ──▶ :core-common
:core-ui        ──▶ :core-common
:core-common    ──▶ (no module deps)
:core-testing   ──▶ shared MockK/JUnit5 fixtures + FakeYouTubeClient/FakeKaraokeRepository (test-only)
```

| Module | What it is |
|---|---|
| `:app` | App shell — `MainActivity`, Compose Navigation host, left nav rail, DI wiring, TV manifest, media-key dispatch. |
| `:core-common` | Dispatcher qualifiers, `AppResult`/`AppError`, `Logger` facade, `SearchSessionHolder`, `MediaKeyRouter` — cross-cutting types every other module can depend on. |
| `:core-ui` | Compose-for-TV theme + reusable focusable components (`FocusableCard`, `KaraloButton`, loading/error states, `KaraokeQrCode`). |
| `:core-network` | Shared OkHttp client (timeouts, logging interceptor). |
| `:core-karaoke` | Karaoke session/queue domain + backend REST/WebSocket client — see "Karaoke remote control" below. |
| `:youtube-client` | The only module allowed to depend on NewPipeExtractor — see "How search & playback work" below. |
| `:feature-search` | Search domain/data/presentation — the "karaoke " prefix, suggestions, results grid. |
| `:feature-player` | Player domain/data/presentation — queue navigation, ExoPlayer integration, controls overlay, karaoke queue/waiting-screen integration. |
| `:feature-home` | Static v1 empty-state screen. |
| `:core-testing` | Shared test fixtures (`MainDispatcherExtension`, `FakeYouTubeClient`, `FakeKaraokeRepository`). |
| `backend/` | Independent Ktor service (session/queue/participants, mobile web app) — not part of the Android multi-module build; wired in via `includeBuild`. |

See `docs/adr/` for the reasoning behind the major choices (Compose for TV over Leanback, Media3,
unofficial extraction over the official YouTube API, module boundaries, the karaoke backend).

## Karaoke remote control

Phones join the TV's karaoke session by scanning a QR code shown at the top of Home, over the
player, or on the "waiting for the next song" screen — no app install, no account. From a plain
mobile browser they enter a name, search, and add songs to a shared queue that plays automatically
when nothing else is. Clicking a Home/Search result on the TV directly always plays instantly
("Play Now") without disturbing that queue; it resumes exactly where it was afterwards.

This is powered by a small self-hosted backend (`backend/`, Ktor + SQLite) that the TV and phones
both talk to — the backend is the single source of truth for the session, participants, and queue;
phones never talk to the TV directly. See
`docs/adr/0005-karaoke-remote-control-session-and-backend.md` for the full design and its
documented MVP-vs-follow-up boundaries (single process, no schema migrations yet). To host it
online so phones can join from any network, see `docs/deploy.md`.

### Running the backend locally

```
./gradlew :backend:run
```

This starts the Ktor server on `0.0.0.0:8080` by default (override with the `KARALO_PORT`/
`KARALO_HOST` env vars; see `backend/src/main/kotlin/com/karalo/backend/config/AppConfig.kt`). Find your machine's
LAN IP (e.g. `ipconfig getifaddr en0` on macOS) — the TV and any phones need to reach that address
on the same Wi-Fi network. macOS will prompt to allow inbound connections the first time; accept
it.

Point the Android app at that backend before building, via env vars (mirroring the existing
`RELEASE_KEYSTORE_*` convention — see `core-karaoke/build.gradle.kts`):

```
export KARALO_BACKEND_BASE_URL=http://<your-lan-ip>:8080
export KARALO_BACKEND_WS_URL=ws://<your-lan-ip>:8080
./gradlew :app:assembleDebug
```

Without these set, the app falls back to a placeholder LAN address that simply won't connect —
manual TV playback (Home/Search → Play Now) works with no backend running at all; only the
queue/QR/remote-add features need one reachable.

Backend-only tests: `./gradlew :backend:test`.

## How search & playback work

There's no official backend. `:youtube-client` wraps
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (the same technique
[SmartTube](https://github.com/yuliskov/smarttube) uses) to search YouTube and resolve direct,
ad-free playable stream URLs, fed straight into Media3/ExoPlayer. **This is unofficial and
against YouTube's Terms of Service** — see `docs/adr/0002-unofficial-youtube-extraction.md` for
the trade-off this was a deliberate choice, and "Known limitations" below for what that costs.

Every other module talks only to the `YouTubeClient` interface, so the extraction strategy can be
swapped later without touching feature code.

## Setup

### Prerequisites

- JDK 17
- Android Studio (latest stable) with an Android SDK (compileSdk/targetSdk 35, minSdk 24)
- An Android TV emulator profile (Android Studio → Device Manager → create device → category
  "TV") or a physical Google TV / Android TV / Chromecast with Google TV device

### First run

```
git clone git@github.com:llocong/karalo.git
cd karalo
```

Open the project in Android Studio — on first sync it will provision the Gradle wrapper
automatically. (If you're on the command line instead and don't have `gradlew` yet, run
`gradle wrapper --gradle-version 8.10.2` once with any locally installed Gradle to generate it —
see gradle/wrapper/gradle-wrapper.properties for the pinned version.)

No secrets are required to build and run in debug — the app ships with a placeholder
`app/google-services.json` (see `docs/firebase-setup.md`) and an unsigned debug build config.
Copy `local.properties.example` to `local.properties` if you need any of the optional local
values it documents (release signing).

```
./gradlew assembleDebug
```

### Running on an Android TV emulator

1. Android Studio → Device Manager → Create device → "TV" category → any Google TV/Android TV
   profile (1080p recommended) → API 30+.
2. Run the `app` configuration against that device.
3. Navigate with the emulator's D-pad controls (arrow keys map to D-pad, Enter to select).

If arrow keys and the Extended Controls D-pad don't do anything, check the AVD's
`~/.android/avd/<name>.avd/config.ini` for `hw.keyboard=no` — some device profiles are created
with the virtual keyboard hardware disabled, which silently drops all D-pad input (both host
keys and the Extended Controls panel) while leaving `adb shell input keyevent` unaffected, since
that path injects into the guest's InputManager directly instead of going through the emulated
keyboard. Set `hw.keyboard=yes` and restart the emulator to fix it.

### Tests

```
./gradlew ktlintCheck detekt      # formatting + static analysis
./gradlew testDebugUnitTest       # unit tests (JUnit5 + MockK)
./gradlew koverVerify             # coverage threshold
./gradlew connectedDebugAndroidTest  # instrumented + Compose UI tests (needs a running emulator/device)
```

## Contributing / branching workflow

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full branch/commit/PR/release workflow
(`main` + short-lived `feature/*` branches, Conventional Commits, required CI + review before
merge, branch protection settings to configure once).

## Known limitations (v1)

- **Unofficial YouTube extraction.** See "How search & playback work" above — this can break when
  YouTube changes its internal APIs, and carries ToS/account-risk that's out of scope for this
  app to mitigate. Play Store distribution is not attempted for this reason; releases ship as
  GitHub Releases (see `.github/workflows/release.yml`).
- **No persistence beyond the karaoke feature.** Search results/queue for manual playback live
  only in memory for the current app session — no watch history, no resume-across-restarts. See
  `docs/adr/0003-no-persistence-in-v1.md`. The karaoke feature's own persistent queue lives on the
  backend, not the TV; see `docs/adr/0005-karaoke-remote-control-session-and-backend.md`.
- **Karaoke backend is LAN-only and self-hosted.** No public hosting/domain is configured; TV
  pairing is trust-on-first-use (acceptable only because the backend isn't internet-exposed in
  this pass); the mobile queue page uses up/down buttons rather than drag-and-drop reorder. See
  the ADR for the full list of documented MVP-vs-follow-up boundaries.
- **No system media integration.** Hardware/remote media keys are handled directly by
  `MainActivity` (see `com.karalo.core.common.mediakeys`), not via a `MediaSession`, so there's no
  lock-screen/notification playback UI or guaranteed Google Assistant voice control.
