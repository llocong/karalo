package com.karalo.backend.config

/**
 * All environment-derived config in one place. Every value has a LAN-dev-friendly default so the
 * server runs with zero setup on a developer machine, per this feature's "local Wi-Fi only for
 * now" MVP scope — set the real env vars before pointing a phone/TV at anything other than
 * localhost. `publicBaseUrl` in particular is what gets encoded into every QR code, so it must be
 * the developer machine's actual LAN IP (e.g. "http://192.168.1.42:8080"), not "localhost" —
 * localhost on the phone/TV would resolve to THAT device, not this server.
 */
data class AppConfig(
    val host: String = System.getenv("KARALO_HOST") ?: "0.0.0.0",
    val port: Int = System.getenv("KARALO_PORT")?.toIntOrNull() ?: 8080,
    val dbPath: String = System.getenv("KARALO_DB_PATH") ?: "karalo-backend.db",
    val publicBaseUrl: String = System.getenv("KARALO_PUBLIC_BASE_URL") ?: "http://localhost:8080",
    /**
     * When set, a TV can only register (its first `session/ensure`) by presenting this key in the
     * `X-Karalo-Registration-Key` header, so strangers can't create sessions on an internet-exposed
     * server. Unset (the default) keeps registration open, as on a home LAN.
     */
    val tvRegistrationKey: String? = System.getenv("KARALO_TV_REGISTRATION_KEY")?.takeIf { it.isNotBlank() },
    /**
     * Set to "true" only when running behind a reverse proxy (e.g. Caddy) that sets
     * `X-Forwarded-For`: rate limits then key on the real client IP instead of the proxy's.
     * Off by default, since without a proxy clients could spoof the header.
     */
    val trustProxy: Boolean = System.getenv("KARALO_TRUST_PROXY")?.toBoolean() ?: false,
    /**
     * Turns on the admin dashboard at /admin: the admin password as `karalo-backend
     * hash-admin-password` prints it (see deploy/set-admin-password.sh). Unset keeps /admin off.
     */
    val adminPasswordHash: String? = System.getenv("KARALO_ADMIN_PASSWORD_HASH")?.takeIf { it.isNotBlank() },
    /** "owner/name" of the GitHub repo whose release downloads the dashboard shows; unset skips them. */
    val githubRepo: String? = System.getenv("KARALO_GITHUB_REPO")?.takeIf { it.isNotBlank() },
    /**
     * Where security alerts are pushed: an ntfy topic URL such as https://ntfy.sh/<random name>
     * (see security/AlertSender). Unset records events on the dashboard without alerting.
     */
    val alertNtfyUrl: String? = System.getenv("KARALO_ALERT_NTFY_URL")?.takeIf { it.isNotBlank() },
) {
    companion object {
        fun fromEnv() = AppConfig()
    }
}
