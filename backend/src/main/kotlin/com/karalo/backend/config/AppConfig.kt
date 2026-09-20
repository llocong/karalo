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
) {
    companion object {
        fun fromEnv() = AppConfig()
    }
}
