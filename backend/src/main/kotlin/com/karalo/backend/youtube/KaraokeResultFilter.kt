package com.karalo.backend.youtube

import java.text.Normalizer

// Matched as whole words/phrases against the normalized (lowercase, accent-free) title. Ignored
// when the user searched for one of the term's words.
private val BLOCKED_TERMS =
    listOf(
        "review", "reviews", "unboxing", "tutorial", "how to", "reaction", "vs",
        "speaker", "speakers", "microphone", "microphones", "mic", "mics", "setup", "vlog",
        "compilation", "top 10", "fails",
    ).associateWith(::wholeWords)

// Karaoke equipment. Always blocked, even when the user searched e.g. "machine" (the prefixed query
// is then literally "karaoke machine"). A song-shaped title ("Karaoke Machine - Imagine Dragons",
// with a dash separator) has its leading "karaoke" stripped first so it isn't mistaken for a
// product; an ad like "Karaoke Machine. Portable and loud." has no separator and stays blocked.
private val EQUIPMENT_TERMS =
    listOf("karaoke machine", "karaoke machines", "karaoke system", "karaoke systems", "karaoke player")
        .map(::wholeWords)
private val LEADING_KARAOKE = Regex("""^kara?oke\b[\s:–-]*""")
private val SONG_SEPARATOR = Regex("""\s[-–]\s""")

// Matched as substrings of the normalized title or channel name.
private val KARAOKE_MARKERS =
    listOf(
        "karaoke", "karoke", "instrumental", "sing along", "sing-along", "singalong",
        "backing track", "off vocal", "minus one", "no vocals", "without vocals",
    )

// Karaoke channels whose video titles don't always say "karaoke". Matched exactly (normalized).
private val KARAOKE_CHANNELS =
    setOf(
        "sing king", "karafun", "zoom karaoke", "stingray karaoke", "karaoke version", "cc karaoke",
        "party tyme karaoke", "sbi karaoke", "musisi karaoke", "atomic karaoke", "global karaoke",
        "karaokeytv",
    )

private const val MIN_DURATION_SECONDS = 60L
private const val MAX_DURATION_SECONDS = 15 * 60L

private val DIACRITICS = Regex("""\p{Mn}+""")
private val WHITESPACE = Regex("""\s+""")

private fun wholeWords(term: String) = Regex("""\b${Regex.escape(term)}\b""")

internal const val MIN_RESULTS_BEFORE_TOP_UP = 10

/**
 * Copied from the Android app's `feature-search/.../domain/KaraokeResultFilter.kt` -- same lists,
 * same rules -- so the phone web page's search drops the same non-karaoke results (reviews,
 * regular music videos, Shorts, compilations) the TV does. Duplicated for the same reason as
 * [formatVideoTitle]: `backend/` is an independent Gradle build. Keep the two in sync.
 *
 * Takes the raw YouTube title (before [formatVideoTitle] strips a leading "karaoke") and
 * [rawQuery], what the user typed: a blocked term is ignored when the user searched for it.
 */
internal fun isLikelyKaraoke(
    title: String,
    channelName: String,
    durationSeconds: Long?,
    rawQuery: String,
): Boolean {
    val duration = durationSeconds ?: return false
    if (duration !in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) return false

    val normalizedTitle = normalize(title)
    val channel = normalize(channelName)
    val queryWords = normalize(rawQuery).split(WHITESPACE).filter { it != "karaoke" }.toSet()

    val equipmentCheckTitle =
        if (SONG_SEPARATOR.containsMatchIn(normalizedTitle)) {
            normalizedTitle.replace(LEADING_KARAOKE, "")
        } else {
            normalizedTitle
        }
    val isBlocked =
        EQUIPMENT_TERMS.any { it.containsMatchIn(equipmentCheckTitle) } ||
            BLOCKED_TERMS.any { (term, pattern) ->
                val searchedFor = term.split(' ').any { it in queryWords }
                !searchedFor && pattern.containsMatchIn(normalizedTitle)
            }
    if (isBlocked) return false

    return KARAOKE_MARKERS.any { it in normalizedTitle || it in channel } || channel in KARAOKE_CHANNELS
}

/** Same as the TV's `filterWithOneTopUp` in `:youtube-client`: at most one extra page. */
internal fun <T> filterWithOneTopUp(
    firstPage: List<T>,
    keep: (T) -> Boolean,
    minResults: Int,
    fetchNextPage: () -> List<T>?,
): List<T> {
    val kept = firstPage.filter(keep)
    if (kept.size >= minResults) return kept
    val nextPage = runCatching(fetchNextPage).getOrNull() ?: return kept
    return kept + nextPage.filter(keep)
}

private fun normalize(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()
        .trim()
