# Karalo

**Karaoke on your TV, with everyone's phone as the remote.**

Karalo is an Android TV / Google TV app for karaoke nights. Search and play karaoke videos from
YouTube, ad-free, in a player built for the TV remote. Guests join from their phones by scanning a
QR code — no app to install and no account — and add songs to a shared queue that plays on the TV.

## Features

**On the TV**

- **Home** — Top Picks, and a banner with the QR code guests scan to join.
- **Search** — with suggestions as you type, and voice search.
- **Playlists** — curated collections: Duets, Pop, Rock, R&B, Latin, Hip-Hop, French Variety,
  Disney, the 60's to 90's, and more.
- **History** — every song sung on the TV, by date or most played, with pause and clear.
- **Player** — play/pause, previous, next and a progress bar; the queue's next song starts on its
  own, and a waiting screen with the QR code shows between songs.
- **Seasonal themes** — Default and Halloween, picked in Settings and shared with every phone in
  the session.

**On the phones** (from any mobile browser, at the address the QR code opens)

- Enter a name and join the TV's session.
- Search, or browse the same playlists as the TV.
- Add songs to the shared queue, reorder it by dragging, and remove songs.
- Pause, resume or skip what's playing.

**Sessions**

- Songs picked on the TV play right away without disturbing the guests' queue, which resumes where
  it left off.
- Guests who've been inactive for two hours are signed out; the session itself ends when the TV
  app is closed or the TV has been off for a while, and the next launch starts a fresh one.

## Install on your TV

On a Google TV or Android TV (for example, a Chromecast with Google TV):

1. From the Play Store, install **Downloader** (by AFTVnews).
2. Allow it to install apps: *Settings > Apps > Security & restrictions > Unknown sources >
   Downloader* (the exact path varies slightly between TVs).
3. Open Downloader, enter `karalo.app/download`, and install when prompted.

Releases are also published on the [Releases](https://github.com/llocong/karalo/releases) page.

## Architecture

Clean Architecture and MVVM across the Android app's Gradle modules, plus an independent backend
service (`backend/`):

```
:app              ──▶ :feature-home, :feature-search, :feature-playlists, :feature-history,
                      :feature-player, :core-karaoke, :core-ui, :core-common
:feature-home     ──▶ :feature-search, :core-ui, :core-common
:feature-playlists──▶ :feature-search, :core-ui, :core-common
:feature-history  ──▶ :core-karaoke, :core-ui, :core-common
:feature-search   ──▶ :youtube-client, :core-ui, :core-common
:feature-player   ──▶ :youtube-client, :core-karaoke, :core-ui, :core-common
:youtube-client   ──▶ :core-network, :core-common
:core-karaoke     ──▶ :core-common
:core-network     ──▶ :core-common
:core-ui          ──▶ :core-common
:core-common      ──▶ (no module dependencies)
```

| Module | Responsibility |
|---|---|
| `:app` | App shell: `MainActivity`, navigation, the side menu, Settings, dependency injection, the TV manifest and media-key handling. |
| `:feature-home` | Home: Top Picks and the join banner. |
| `:feature-search` | Search: query field, suggestions, voice search and results. |
| `:feature-playlists` | The Playlists page. |
| `:feature-history` | The History page. |
| `:feature-player` | The player: ExoPlayer, controls, queue playback and the waiting screen. |
| `:core-karaoke` | The karaoke session and queue, and the client for the backend's REST and WebSocket APIs. |
| `:youtube-client` | The only module that talks to YouTube (see "How search and playback work"). |
| `:core-ui` | Compose for TV theme, seasonal themes and shared focusable components. |
| `:core-network` | The shared HTTP client. |
| `:core-common` | Cross-cutting types: `AppResult`/`AppError`, dispatchers, logging. |
| `:core-testing` | Shared test fixtures and fakes. |
| `:baselineprofile` | Generates the Baseline Profile that speeds up startup and scrolling. |
| `backend/` | Ktor + SQLite service for sessions, guests, the queue and history, which also serves the phones' web app. A separate Gradle build, included via `includeBuild`. |

The reasoning behind the major choices — Compose for TV, Media3, unofficial YouTube extraction,
the karaoke backend — is recorded in [`docs/adr/`](docs/adr/).

## How search and playback work

`:youtube-client` wraps [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) to
search YouTube and resolve ad-free playable streams, which Media3/ExoPlayer plays directly. The
rest of the app only depends on its `YouTubeClient` interface, so the extraction strategy can
change without touching feature code.

**This extraction is unofficial and not permitted by YouTube's Terms of Service.** See
[`docs/adr/0002-unofficial-youtube-extraction.md`](docs/adr/0002-unofficial-youtube-extraction.md)
for why it was chosen and what it costs.

## Backend

The TV and the phones both talk to the backend, which is the single source of truth for each TV's
session, its guests and its queue; phones never talk to the TV directly. The design is described in
[`docs/adr/0005-karaoke-remote-control-session-and-backend.md`](docs/adr/0005-karaoke-remote-control-session-and-backend.md),
and hosting it online in [`docs/deploy.md`](docs/deploy.md). The production instance runs at
[karalo.app](https://karalo.app).

### Running it locally

```
./gradlew :backend:run
```

The server listens on `0.0.0.0:8080` by default (see
`backend/src/main/kotlin/com/karalo/backend/config/AppConfig.kt` for the `KARALO_*` settings). The
TV and phones need to reach your machine's LAN address (`ipconfig getifaddr en0` on macOS). Point
the app at it before building, in `local.properties` or as environment variables:

```
KARALO_BACKEND_BASE_URL=http://<your-lan-ip>:8080
KARALO_BACKEND_WS_URL=ws://<your-lan-ip>:8080
```

Without a reachable backend, playback from the TV still works; only joining from phones and the
shared queue need one.

## Development

### Prerequisites

- JDK 17
- Android Studio (latest stable) with the Android SDK (compile/target SDK 35, min SDK 24)
- An Android TV emulator (Device Manager → Create device → TV) or a Google TV / Android TV device

### Build

```
git clone https://github.com/llocong/karalo.git
cd karalo
./gradlew assembleDebug
```

No secrets are needed to build and run a debug build: the repository includes a placeholder
`app/google-services.json` (see [`docs/firebase-setup.md`](docs/firebase-setup.md)). Copy
`local.properties.example` to `local.properties` for the optional local settings it documents.

Performance should be judged on a release build on real TV hardware: debug builds are never
compiled ahead of time and feel noticeably slower. See [CONTRIBUTING.md](CONTRIBUTING.md)
"Performance".

### Running on an emulator

1. Create a TV device in Android Studio's Device Manager (1080p, API 30 or later).
2. Run the `app` configuration on it.
3. Use the arrow keys as the D-pad and Enter to select.

If the arrow keys do nothing, check the emulator's `~/.android/avd/<name>.avd/config.ini`: some
TV profiles are created with `hw.keyboard=no`, which drops all D-pad input. Set `hw.keyboard=yes`
and restart the emulator.

### Tests

```
./gradlew ktlintCheck detekt          # formatting and static analysis
./gradlew testDebugUnitTest           # unit tests (JUnit 5 + MockK)
./gradlew koverVerify                 # coverage threshold
./gradlew connectedDebugAndroidTest   # instrumented and Compose UI tests (needs an emulator or device)
./gradlew :backend:test               # backend tests
```

## Contributing and releases

[CONTRIBUTING.md](CONTRIBUTING.md) covers the branch, commit and pull request workflow, and how
releases are built, signed and published.

## Known limitations

- **Unofficial YouTube extraction.** It can break when YouTube changes its internal APIs, and it
  rules out distribution through the Play Store.
- **Searches from phones go through the backend.** They're made from the server's address, so heavy
  use can get it rate-limited by YouTube.
- **Single backend instance.** The backend is one process with a SQLite database; it isn't set up
  to run on several servers.
- **No system media integration.** Remote media keys are handled by the app itself rather than a
  `MediaSession`, so there's no system playback UI and Google Assistant voice control isn't
  guaranteed.
