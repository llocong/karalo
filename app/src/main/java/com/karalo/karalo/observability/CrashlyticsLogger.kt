package com.karalo.karalo.observability

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.karalo.core.common.logging.Logger
import javax.inject.Inject

/**
 * Safe to use even before a real Firebase project is configured: with the checked-in placeholder
 * google-services.json (see docs/firebase-setup.md), Crashlytics logs a warning and no-ops rather
 * than throwing.
 */
class CrashlyticsLogger
    @Inject
    constructor() : Logger {
        override fun log(message: String) {
            FirebaseCrashlytics.getInstance().log(message)
        }

        override fun recordException(throwable: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        }

        override fun recordEvent(
            name: String,
            params: Map<String, String>,
        ) {
            val details = params.entries.joinToString { (key, value) -> "$key=$value" }
            FirebaseCrashlytics.getInstance().log("event: $name${if (details.isNotEmpty()) " ($details)" else ""}")
        }
    }
