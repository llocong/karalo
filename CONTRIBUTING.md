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

The coverage floor (`build.gradle.kts`, currently 20% overall line coverage) is intentionally low
— it measures the whole codebase, and a large share of it is Compose UI/DI modules that this
project doesn't unit-test by design (UI flows are covered by the instrumented nav test instead).
Domain/data/ViewModel logic is close to fully covered; when you add well-tested logic, feel free
to ratchet the floor up in the same PR, but don't raise it just to make a failing PR pass.

If you touched UI, also run the instrumented tests against an Android TV emulator
(`./gradlew connectedDebugAndroidTest`) and manually verify D-pad focus behavior — see
[README.md](README.md#running-on-an-android-tv-emulator).

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

Merges to `main` trigger `release.yml`, which uses `release-please` to open/update a "release PR"
that bumps the version and updates `CHANGELOG.md` from Conventional Commits. Merging that PR (or
pushing a `vX.Y.Z` tag directly) builds a signed AAB and attaches it to a GitHub Release — see
`app/build.gradle.kts` for `versionCode`/`versionName`, and bump `versionCode` by hand alongside
that PR (it isn't derived automatically). Play Console publishing is opt-in — see README
"Secrets".

## Secrets you'll need locally

Copy `local.properties.example` to `local.properties` (gitignored) and fill in any values you
need for the workflows you're touching — none are required just to build/run in debug.
