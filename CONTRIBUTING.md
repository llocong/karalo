# Contributing to Karalo

## Branching model

- `main` is always releasable. No direct commits to `main`.
- Work happens on short-lived `feature/<short-description>` branches off `main`.
- Every change lands via a pull request into `main`, with CI green and at least one approving
  review (see the branch protection settings below).

## Commit convention

This repo follows [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add autocomplete to the search screen
fix: prevent double "karaoke " prefix on resubmit
chore: bump NewPipeExtractor to v0.24.1
docs: document Firebase setup
refactor: extract StreamSelection out of NewPipeYouTubeClient
test: cover PlaybackQueue previous/next edge cases
ci: cache Gradle dependencies in the release workflow
```

`release.yml` uses these to derive the next semantic version and generate `CHANGELOG.md` entries
automatically (via `release-please`) — inaccurate commit types will produce an inaccurate
changelog, so take a moment to pick the right one.

## Before opening a PR

```
./gradlew ktlintCheck detekt   # formatting + static analysis
./gradlew testDebugUnitTest    # unit tests (JUnit5 + MockK)
./gradlew koverVerify          # coverage threshold (see note below)
./gradlew assembleDebug        # sanity build across all modules
```

The coverage floor (`build.gradle.kts`, currently 18% overall line coverage) is intentionally low
— it measures the whole codebase, and a large share of it is Compose UI/DI modules that this
project doesn't unit-test by design (UI flows are covered by the instrumented nav test instead).
Domain/data/ViewModel logic is close to fully covered; when you add well-tested logic, feel free
to ratchet the floor up in the same PR, but don't raise it just to make a failing PR pass.

If you touched UI, also run the instrumented tests against an Android TV emulator
(`./gradlew connectedDebugAndroidTest`) and manually verify D-pad focus behavior — see
[README.md](README.md#running-on-an-android-tv-emulator).

If you touched `backend/`, also run `./gradlew :backend:test` — it's a separate, independent
Gradle build (`includeBuild`), not part of the Android module's `ktlintCheck`/`detekt`/
`koverVerify` aggregation, so those root-level checks don't cover it.

## Pull requests

Use the PR template checklist. CI (`ci.yml`) runs ktlint/detekt, unit tests + coverage, an
assemble sanity build, and instrumented tests on a TV emulator profile on every PR — all must be
green before merge.

## Branch protection (not yet configured)

**Current status:** this repo is private on GitHub's free plan, and neither classic branch
protection rules nor the newer rulesets API are available at that tier (GitHub returns "Upgrade
to GitHub Pro or make this repository public"). For a solo v1 this isn't blocking — follow the
branching/PR discipline in this doc regardless of whether GitHub enforces it — but it means `main`
is not actually protected from direct pushes or force-pushes yet.

Revisit this once the repo goes public or gets a paid plan, or once there's more than one
contributor. When you do, configure on `main`:

- Require a pull request before merging (no direct pushes).
- Require status checks to pass: `ktlint & detekt`, `Unit tests & coverage`, `Assemble debug`,
  `Instrumented tests (Android TV emulator)`.
- Require branches to be up to date before merging.
- Require at least 1 approving review; dismiss stale approvals on new commits.
- Do not allow force pushes; do not allow branch deletion.

## Releasing

Releases are built and signed by CI, recorded as GitHub Releases, and served to friends and family
from `https://karalo.app/download`.

1. Merge work into `main`. `release.yml` runs `release-please`, which opens (or updates) a
   "chore(main): release X.Y.Z" PR bumping `.release-please-manifest.json` and `CHANGELOG.md` from
   Conventional Commits. `app/build.gradle.kts` reads `versionName` from that manifest and derives
   `versionCode` from it (major×1,000,000 + minor×1,000 + patch), so nothing is bumped by hand.
2. Merge that PR. release-please tags `vX.Y.Z` and creates the GitHub Release; the same workflow
   then builds the release APK and AAB, refuses to continue if the APK isn't signed with the
   release key, and attaches `karalo-X.Y.Z.apk`, its `.sha256`, the `.aab` and `latest.json`
   (version, APK URL, checksum and release notes: what an in-app update check will read).
3. Publish it to karalo.app when you're ready:

   ```
   deploy/publish-apk.sh vX.Y.Z
   ```

   It downloads the release's files, checks the checksum, and puts them in `/srv/karalo/download`
   on the VM; `karalo.app/download` redirects to the newest APK. Running it with an older tag
   rolls back.

### One-time setup

**Release signing key.** Android only installs an update signed with the same key as the installed
app, so this key is forever: losing it means every TV has to uninstall and reinstall (which also
resets its session and QR code). Create it once, on your own machine, and keep the keystore and its
passwords in a password manager:

```
keytool -genkeypair -v -keystore ~/karalo-release.keystore -alias karalo \
  -keyalg RSA -keysize 4096 -validity 10000
```

**Repository secrets and variables** (`gh` prompts for the values it doesn't read from a file, so
they stay out of your shell history):

```
base64 -i ~/karalo-release.keystore | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS --body karalo
gh secret set RELEASE_KEY_PASSWORD
grep '^KARALO_TV_REGISTRATION_KEY=' local.properties | cut -d= -f2- | gh secret set KARALO_TV_REGISTRATION_KEY
gh variable set KARALO_BACKEND_BASE_URL --body https://karalo.app
gh variable set KARALO_BACKEND_WS_URL --body wss://karalo.app
```

The registration key ends up inside the published APK, where anyone who has the file can extract
it: it keeps out strangers' TVs only as long as the APK isn't public. `GOOGLE_SERVICES_JSON_BASE64`
is optional (Firebase Crashlytics).

**Moving a TV to the release key.** A TV running a locally built (debug-signed) APK can't update to
a release: uninstall Karalo there once, then install the release.

## Performance

`:app` ships a Baseline Profile (`app/src/release/generated/baselineProfiles/baseline-prof.txt`,
generated by the `:baselineprofile` module) so ART AOT-compiles the app's Compose/scroll-animation
hot paths on a real release build, instead of JIT-compiling them live on first use
— a debug build never gets this (debuggable apps are never AOT-compiled), so a debug build's
scroll/focus animations will always look choppier on real, low-powered TV hardware than they
actually ship. Test animation feel against a release (or `nonMinifiedRelease`) build, not debug.

A Play Store install compiles the profile at install time, but a sideload (`adb install`, or the
Downloader app from karalo.app/download) doesn't: the app
runs uncompiled until the TV's idle maintenance gets to it. On the Chromecast with Google TV that
measured 2.1 s cold start uncompiled vs 0.72 s compiled (2026-09-25), so after sideloading a
release build, compile it right away:

```
adb shell cmd package compile -m speed-profile -f com.karalo.karalo
```

Regenerate the profile whenever a major user-facing flow changes meaningfully (e.g. a new/changed
Home shelf interaction) — it's a snapshot of which classes/methods a specific flow touches, not
something that self-updates:

```
./gradlew :app:generateReleaseBaselineProfile
```

Requires exactly one connected device or emulator (API 28+); set `ANDROID_SERIAL` if more than one
is attached. Commit the resulting `app/src/release/generated/baselineProfiles/baseline-prof.txt`.
`automaticGenerationDuringBuild` is deliberately off (see `app/build.gradle.kts`) since CI's
release workflow runs on a plain Ubuntu runner with no device attached.

## Secrets you'll need locally

Copy `local.properties.example` to `local.properties` (gitignored) and fill in any values you
need for the workflows you're touching — none are required just to build/run in debug.
