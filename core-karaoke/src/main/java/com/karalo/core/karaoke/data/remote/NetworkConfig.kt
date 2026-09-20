package com.karalo.core.karaoke.data.remote

import com.karalo.core.karaoke.BuildConfig

/**
 * Resolves the karaoke backend's REST/WS base URLs. No production URL is hardcoded — this
 * feature's current scope is local-LAN development/testing only (see the karaoke ADR). Override
 * per-machine by setting `KARALO_BACKEND_BASE_URL`/`KARALO_BACKEND_WS_URL` as an env var or a
 * `gradle.properties` entry before building, mirroring the `RELEASE_KEYSTORE_*` precedent in
 * `app/build.gradle.kts` — point them at your own Mac's LAN IP running the backend service.
 */
object NetworkConfig {
    val restBaseUrl: String = BuildConfig.DEFAULT_BACKEND_BASE_URL
    val wsBaseUrl: String = BuildConfig.DEFAULT_BACKEND_WS_URL
}
