# Karalo

A native Android TV / Google TV app for karaoke: search YouTube and sing along, ad-free, with a
minimal remote-friendly player. Every search is silently prefixed with `karaoke ` — type
"Rihanna" and it searches "karaoke Rihanna".

**v1 scope is intentionally small:** a left-nav with Home and Search, YouTube search with
autocomplete, a results grid, and a stripped-down player (Play/Pause, Previous, Next, progress
bar only). No microphone/scoring, no accounts, no monetization.

## Architecture

Clean Architecture + MVVM across 9 Gradle modules:

```
:app  ──▶ :feature-search, :feature-player, :feature-home, :core-ui, :core-common
:feature-search ──▶ :youtube-client, :core-ui, :core-common
:feature-player ──▶ :youtube-client, :core-ui, :core-common
:feature-home   ──▶ :core-ui, :core-common
:youtube-client ──▶ :core-network, :core-common
:core-network   ──▶ :core-common
:core-ui        ──▶ :core-common
:core-common    ──▶ (no module deps)
:core-testing   ──▶ shared MockK/JUnit5 fixtures + FakeYouTubeClient (test-only)
```

| Module | What it is |
|---|---|
| `:app` | App shell — `MainActivity`, Compose Navigation host, left nav rail, DI wiring, TV manifest, media-key dispatch. |
| `:core-common` | Dispatcher qualifiers, `AppResult`/`AppError`, `Logger` facade, `SearchSessionHolder`, `MediaKeyRouter` — cross-cutting types every other module can depend on. |
| `:core-ui` | Compose-for-TV theme + reusable focusable components (`FocusableCard`, `KaraloButton`, loading/error states). |
| `:core-network` | Shared OkHttp client (timeouts, logging interceptor). |
| `:youtube-client` | The only module allowed to depend on NewPipeExtractor — see "How search & playback work" below. |
| `:feature-search` | Search domain/data/presentation — the "karaoke " prefix, suggestions, results grid. |
| `:feature-player` | Player domain/data/presentation — queue navigation, ExoPlayer integration, controls overlay. |
| `:feature-home` | Static v1 empty-state screen. |
| `:core-testing` | Shared test fixtures (`MainDispatcherExtension`, `FakeYouTubeClient`). |

See `docs/adr/` for the reasoning behind the major choices (Compose for TV over Leanback, Media3,
unofficial extraction over the official YouTube API, module boundaries).

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
- **No persistence.** Search results/queue live only in memory for the current app session — no
  watch history, no resume-across-restarts. See `docs/adr/0003-no-persistence-in-v1.md`.
- **No system media integration.** Hardware/remote media keys are handled directly by
  `MainActivity` (see `com.karalo.core.common.mediakeys`), not via a `MediaSession`, so there's no
  lock-screen/notification playback UI or guaranteed Google Assistant voice control.
- **Placeholder art.** The launcher icon and Google TV banner (`app/src/main/res/drawable/`) are
  simple vector placeholders, not final brand assets.
