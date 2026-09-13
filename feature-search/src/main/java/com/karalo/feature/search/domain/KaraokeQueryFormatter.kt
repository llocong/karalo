package com.karalo.feature.search.domain

/**
 * Owns the "karaoke " query rewrite so it's applied consistently to both autocomplete and final
 * search, and is testable independent of the UI (which must keep echoing the user's raw text)
 * and the data layer (which stays a dumb pass-through, reusable for any future non-karaoke
 * search feature).
 */
object KaraokeQueryFormatter {
    private const val PREFIX = "karaoke "

    fun format(rawUserInput: String): String {
        val trimmed = rawUserInput.trim()
        return if (trimmed.startsWith(PREFIX, ignoreCase = true)) trimmed else PREFIX + trimmed
    }
}
