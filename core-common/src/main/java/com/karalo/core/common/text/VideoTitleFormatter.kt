package com.karalo.core.common.text

// Matches a leading "Karaoke "/"Karaoké "/"Karoke "/"Karoké " (and an optional ":"/"-"
// separator) some channels — e.g. KaraFun — tack onto the front of a video's title, such as
// "Karaoké Je te donne - Jean-Jacques Goldman". The (?![\p{L}]) guard stops it from also eating
// the start of an unrelated word that merely begins with those letters.
private val LEADING_KARAOKE_PREFIX = Regex("""(?i)^kara?ok[ée](?![\p{L}])[\s:–-]*""")

// Matches a trailing " | ..." section, e.g. " | Karaoke Version | Karafun".
private val TRAILING_PIPE_SUFFIX = Regex("""\s*\|.*$""")

// Matches a single trailing "(...)" section, e.g. " (Karaoke Version)".
private val TRAILING_PAREN_SUFFIX = Regex("""\s*\([^()]*\)\s*$""")

/**
 * Strips the promotional/branding search results commonly tack onto a video's title — a leading
 * "Karaoke "/"Karaoké " and/or a trailing "(Karaoke Version)" or "| Karaoke Version | Karafun" —
 * leaving just the artist and song title. Shared by the search results grid and the player's
 * title display. Falls back to the raw title when stripping would leave nothing -- e.g.
 * "KARAOKE | Ai Chung Tình Được Mãi | Tone Nữ", where the song itself sits after the first pipe.
 */
fun formatVideoTitle(rawTitle: String): String =
    rawTitle
        .replace(LEADING_KARAOKE_PREFIX, "")
        .replace(TRAILING_PIPE_SUFFIX, "")
        .replace(TRAILING_PAREN_SUFFIX, "")
        .trimEnd()
        .ifBlank { rawTitle.trim() }
