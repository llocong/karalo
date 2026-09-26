package com.karalo.core.common.model

/**
 * The host-selected seasonal look for a karaoke session -- chosen from the TV's Settings, stored
 * per session on the backend, and applied to the TV app and to every phone joined to that session.
 * [wireName] is the exact string the backend API uses for it.
 */
enum class SeasonalTheme(
    val wireName: String,
) {
    DEFAULT("DEFAULT"),
    HALLOWEEN("HALLOWEEN"),
    ;

    companion object {
        /** Unknown or missing values (e.g. an older backend) fall back to [DEFAULT]. */
        fun fromWireName(value: String?): SeasonalTheme = entries.firstOrNull { it.wireName == value } ?: DEFAULT
    }
}
