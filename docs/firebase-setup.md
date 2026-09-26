# Firebase / Crashlytics setup

The app ships with a **placeholder** `app/google-services.json` committed to the repo (dummy
project id, dummy API key) so `./gradlew assembleDebug`, CI, and every other build task work with
zero setup — the Google Services and Crashlytics Gradle plugins just need *a* file to parse, and
with the placeholder in place they silently no-op instead of failing.

To get real crash/error reporting:

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with package name `com.karalo.karalo`.
3. Download the real `google-services.json` it gives you.
4. **Locally:** replace `app/google-services.json` with the real file. **Do not** `git add` or
   commit that change — it's the same tracked path as the placeholder, so make sure `git status`
   shows it as a local-only modification before you push anything. (Optional extra safety net:
   `git update-index --skip-worktree app/google-services.json` so it stops showing as modified —
   run `git update-index --no-skip-worktree app/google-services.json` if you ever need to.)
5. **In CI:** base64-encode the real file and store it as the `GOOGLE_SERVICES_JSON_BASE64`
   GitHub Actions secret. `release.yml` decodes it over the placeholder before building, so
   release builds report real crashes without ever committing the real file.

## What gets reported

`com.karalo.core.common.logging.Logger` is the app-wide facade (see `:core-common`); `:app` binds
it to `CrashlyticsLogger`. Currently wired up:

- Playback errors (`PlayerViewModel`'s `Player.Listener.onPlayerError`).
- Search/extraction failures surfaced as `AppError.Extraction` from `:youtube-client`.

## Before turning on real reporting

The privacy policy (`backend/src/main/resources/web/privacy.html`) currently says the TV app has
no crash reporting. Setting the `GOOGLE_SERVICES_JSON_BASE64` secret changes that, so update the
policy (English and French) in the same change: what a crash report contains, that it goes to
Google (Firebase), and how long Firebase keeps it.

## No Firebase Analytics

The app doesn't include Firebase Analytics, and `AndroidManifest.xml` sets
`firebase_analytics_collection_deactivated` so it stays off even if a library pulls it in. Usage
numbers (installs, active TVs) come from Karalo's own backend instead.
