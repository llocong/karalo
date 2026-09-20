package com.karalo.backend.youtube

/**
 * Copied verbatim from the Android app's `core-common/.../text/VideoTitleFormatter.kt` -- same
 * regexes, same behavior -- so the mobile web page shows exactly the same cleaned-up titles as
 * the TV does. Duplicated rather than shared as a Gradle source set for the same reason
 * [BackendYouTubeSearch] duplicates NewPipeExtractor's bootstrap logic: `backend/` is a
 * deliberately independent Gradle build (see the karaoke ADR) and can't depend on an Android
 * library module. If the app's version ever changes, mirror the change here too.
 */
private val LEADING_KARAOKE_PREFIX = Regex("""(?i)^kara?ok[ée](?![\p{L}])[\s:–-]*""")
private val TRAILING_PIPE_SUFFIX = Regex("""\s*\|.*$""")
private val TRAILING_PAREN_SUFFIX = Regex("""\s*\([^()]*\)\s*$""")

fun formatVideoTitle(rawTitle: String): String =
    rawTitle
        .replace(LEADING_KARAOKE_PREFIX, "")
        .replace(TRAILING_PIPE_SUFFIX, "")
        .replace(TRAILING_PAREN_SUFFIX, "")
        .trimEnd()
