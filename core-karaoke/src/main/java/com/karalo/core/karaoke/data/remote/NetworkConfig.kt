package com.karalo.core.karaoke.data.remote

import com.karalo.core.karaoke.BuildConfig

/**
 * Resolves the karaoke backend's REST/WS base URLs. No production URL is hardcoded. Set
 * `KARALO_BACKEND_BASE_URL`/`KARALO_BACKEND_WS_URL` (and, for a hosted backend,
 * `KARALO_TV_REGISTRATION_KEY`) as an env var, a `gradle.properties` entry or in `local.properties`
 * before building, mirroring the `RELEASE_KEYSTORE_*` precedent in `app/build.gradle.kts`: either a
 * Mac's LAN IP running the backend, or the hosted server from `docs/deploy.md`.
 */
object NetworkConfig {
    val restBaseUrl: String = BuildConfig.DEFAULT_BACKEND_BASE_URL
    val wsBaseUrl: String = BuildConfig.DEFAULT_BACKEND_WS_URL

    /** Sent when this TV first registers with the backend; empty when the backend doesn't need one. */
    val tvRegistrationKey: String = BuildConfig.TV_REGISTRATION_KEY

    /** This build's version name, e.g. "0.3.0", reported to the backend on every session/ensure. */
    val appVersion: String = BuildConfig.APP_VERSION
}
