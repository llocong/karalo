package com.karalo.core.common.text

// Matches a trailing " | ..." section, e.g. " | Karaoke Version | Karafun".
private val TRAILING_PIPE_SUFFIX = Regex("""\s*\|.*$""")

// Matches a single trailing "(...)" section, e.g. " (Karaoke Version)".
private val TRAILING_PAREN_SUFFIX = Regex("""\s*\([^()]*\)\s*$""")

/**
 * Strips the promotional/branding suffix search results commonly tack onto a video's title (e.g.
 * "Artist - Song (Karaoke Version)" or "Artist - Song | Karaoke Version | Karafun"), leaving just
 * the artist and song title. Shared by the search results grid and the player's title display.
 */
fun formatVideoTitle(rawTitle: String): String =
    rawTitle
        .replace(TRAILING_PIPE_SUFFIX, "")
        .replace(TRAILING_PAREN_SUFFIX, "")
        .trimEnd()
