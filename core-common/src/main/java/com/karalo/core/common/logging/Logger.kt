package com.karalo.core.common.logging

/**
 * Facade over crash/analytics reporting so `:core-common` and every module built on it never
 * depend on Firebase directly. The default binding is a no-op until Crashlytics is configured
 * (see docs/firebase-setup.md); a real binding is provided from `:app`.
 */
interface Logger {
    fun log(message: String)

    fun recordException(throwable: Throwable)

    fun recordEvent(
        name: String,
        params: Map<String, String> = emptyMap(),
    )
}
