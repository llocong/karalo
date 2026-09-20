# ADR-0005: Karaoke remote-control session, backend, and TV-side persistence

**Status:** Accepted (supersedes part of ADR-0003)

## Context

Karalo gained a KaraFun-style feature: phones join the TV's karaoke session via a QR code (no app
install) to search songs and add them to a shared queue, while the TV's existing manual
Home/Search selection remains an instant "Play Now" that never disturbs that queue. This requires
three things ADR-0003 explicitly said v1 didn't need: a server the TV and phones both trust as
source of truth, realtime sync, and a small piece of durable TV-local state (a stable installation
identity) — exactly the "when a real need appears" trigger ADR-0003 called out for itself.

Key constraints from the product requirements:
- Exactly one persistent karaoke session per TV installation, auto-created/restored on launch —
  never manually created.
- Session, Playback, and Queue are three separate concepts with independent lifecycles: leaving
  the player must not end the session, clear the queue, or invalidate the QR code.
- The backend must be the sole source of truth; realtime sync flows backend→TV and backend→phone,
  never phone→TV directly (phones never learn the TV's address or push to it).
- Queue positions are assigned atomically server-side — never client-computed (`length + 1`),
  which breaks under concurrent adds from multiple phones.
- This pass targets a self-hosted MVP proven on a home LAN, not a hosted multi-tenant product.

## Decision

**A new, independent Ktor backend service (`backend/`)**, wired into the root build via
`includeBuild("backend")` in `settings.gradle.kts` rather than as an Android library module. It is
a plain JVM service (Ktor 3 + Netty, Exposed over SQLite via `sqlite-jdbc`/HikariCP,
kotlinx.serialization, Ktor WebSockets) with zero Android/Gradle-AGP conventions, so it can be
built, tested, and run entirely with `./gradlew :backend:...` independent of an Android toolchain.
It owns the session/participant/queue schema and is the only writer of queue order and
now-playing state; the TV and phones are both clients of it.

**Atomicity via serialization, not row locking**: HikariCP's pool is capped at
`maximumPoolSize = 1`, so at most one transaction is ever in flight — "read cursor, increment,
insert at that position" is race-free by construction with no manual locking. Verified with a
20-way-concurrent-add test asserting exactly positions `1..N`, no duplicates or gaps. Documented
ceiling: this serializes all writes process-wide, which is fine for one karaoke party's pace and a
deliberate MVP simplification — a Postgres + `SELECT ... FOR UPDATE` swap is the documented
follow-up if this ever needs to scale past a single backend process.

**One session per TV, enforced at the schema level**: `sessions.tv_installation_id` is `UNIQUE`,
so "ensure session" is a genuine idempotent create-or-restore (trust-on-first-use for the TV
secret), and the database itself — not just application logic — makes a duplicate session for the
same TV installation impossible.

**A stable TV installation ID, persisted via DataStore Preferences** (new `:core-karaoke` module,
`TvInstallationIdProviderImpl`): a self-generated UUID rather than `ANDROID_ID` (which can change
across factory resets and is per-signing-key on some OEM TV builds), generated once under a
`Mutex` and reused forever. This is the one piece of local persistence ADR-0003 deferred — it is
exactly one async-native value, so DataStore Preferences (not Room, not SharedPreferences) matches
this app's all-coroutines style without pulling in a database for one string.

**Session, Playback, and Queue kept structurally decoupled on the TV side**: `KaraokeRepository`
(session/queue/participants, `@Singleton`, outlives any screen) is a separate concern from
`PlayerViewModel`'s local `PlaybackQueue` (what ExoPlayer is actually playing right now, scoped to
the Player nav entry). They meet at exactly one integration point — natural playback completion
(`STATE_ENDED`) calls `consumeNext()` — and at Play-Now start/end, which snapshot around the
persistent queue without ever touching it. Manual TV selection continues to flow through the
existing `SearchSessionHolder` hand-off unchanged (see ADR-0003); a new `KaraokeSessionHolder`
reuses that identical mechanism for the auto-navigate ("a phone added the first song") path.

**Wholesale snapshot reconciliation over WebSocket**, not incremental patching: every WS event
triggers a full `GET queue` refetch client-side rather than field-by-field merging, mirroring
`SearchSessionHolder`'s existing "newest wins" spirit. A stale event landing in the small race
window before a fresher REST response arrives is self-correcting, not a bug worth complex sequence
numbering for at this scale.

**QR codes carry only a public join URL** (session code, no secrets), generated with
`com.google.zxing:core` (pure JVM, TV only ever displays codes, never scans). The join URL's host
comes from build-time env config (`KARALO_BACKEND_BASE_URL`), mirroring the existing
`RELEASE_KEYSTORE_*` convention — never a hardcoded production URL.

## Consequences

- **New always-on dependency for the karaoke feature**: the TV app now expects a reachable
  backend for anything beyond plain manual playback; manual Home/Search Play-Now still works with
  no backend running (it never touches `KaraokeRepository`'s network calls in a way that blocks
  playback), but queue/QR/remote-add features are simply idle without one.
- **LAN-only in this pass**: no public hosting is configured; the user deploys the backend
  themselves later for off-LAN use, and the default `KARALO_BACKEND_BASE_URL` is a placeholder LAN
  IP that fails closed rather than pointing anywhere real.
- **Trust-on-first-use TV claiming** is acceptable only because the backend isn't
  internet-exposed in this pass; a real pairing PIN is a documented follow-up before any public
  deployment.
- **Single-process, in-memory WebSocket registry** — no horizontal scaling. Redis pub/sub is the
  documented follow-up if ever run as more than one backend process.
- **No schema migration tooling** — the SQLite schema is created fresh, not versioned. Acceptable
  for a self-hosted MVP; a migration tool (e.g. Flyway) is a follow-up once the schema needs to
  evolve under real deployed data.
- **TV installation ID cannot survive app uninstall or data clear** — an honest Android platform
  limitation (app-private storage), not something this design works around. A fresh install
  legitimately gets a new session.
- ADR-0003's "no persistence" premise is superseded for this one value; its broader point (don't
  speculatively build a database for the whole app) still stands — this is the smallest possible
  persistence surface (one DataStore key) for the actual need that appeared.

## Bugs found during manual verification (fixed, not just noted)

Manual end-to-end testing against a real running backend (not just unit tests) surfaced two real
defects that no amount of unit testing alone would have caught, since both are specifically about
behavior across process/app boundaries:

- **Cleartext HTTP was silently blocked.** Android's default Network Security Config
  (targetSdk 28+) refuses cleartext traffic app-wide unless declared otherwise, and this app never
  had a `network_security_config.xml`. Every request to the backend failed silently (no crash, no
  logged exception reaching logcat — see `CrashlyticsLogger`, which no-ops against the placeholder
  Firebase project) — the QR simply never appeared. Fixed by adding
  `app/src/main/res/xml/network_security_config.xml` (cleartext permitted app-wide, since the
  backend's LAN address isn't a fixed hostname that could be allow-listed) and wiring it via
  `android:networkSecurityConfig` in the manifest.
- **A Play-Now session left via BACK before the song ended stayed stuck forever.**
  `PlayerViewModel.onPlaybackEnded()` was the only place that called `playNowEnd()`; leaving the
  player any other way (BACK, in practice) skipped it entirely, so the backend's `nowPlaying`
  stayed pinned to the abandoned Play-Now song — permanently blocking the persistent queue from
  ever resuming, since a new item only auto-promotes to now-playing when nothing else already is.
  Fixed by also calling `playNowEnd()` from `onCleared()` when `isPlayNowActive`, launched on the
  `@ApplicationScope` scope (not `viewModelScope`, which is already cancelled by the time
  `onCleared()` runs).
- **The TV secret was never persisted, only cached in memory.** The backend's `session/ensure` is
  trust-on-first-use: only the very first call for a TV installation may omit the secret, every
  call after must present the one issued then. `KaraokeRepositoryImpl` cached it in a plain
  `@Volatile var`, so restarting the app (found by force-stopping and relaunching against a live
  backend) lost it — every subsequent `ensureSession()` call was rejected with 401, permanently
  locking that TV out of its own already-created session with no recovery short of deleting it
  server-side. Fixed by persisting it in the same `@KaraokeDataStore` used for the TV installation
  ID, read back on `ensureSession()` when not already cached in memory.
