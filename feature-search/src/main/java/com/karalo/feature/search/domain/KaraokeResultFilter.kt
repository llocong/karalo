package com.karalo.feature.search.domain

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

/**
 * Decides whether a search result is actually a karaoke track. The "karaoke " query prefix
 * (see [KaraokeQueryFormatter]) only nudges YouTube -- "machine" becomes "karaoke machine", which
 * returns karaoke-machine reviews and regular music videos -- so results are also checked here.
 *
 * Allow-list, not block-list: a result is kept only on positive evidence it's karaoke (a marker
 * in its title/channel, or a known karaoke channel), after rejecting obvious junk and anything
 * whose length doesn't look like a single song. Mirrored in the backend's
 * `KaraokeResultFilter.kt` -- keep the two in sync.
 */
object KaraokeResultFilter {
    /** Below this many kept results, one more page of YouTube results is fetched and filtered. */
    const val MIN_RESULTS_BEFORE_TOP_UP = 10

    /**
     * [rawQuery] is what the user typed (without the "karaoke " prefix). A blocked term is
     * ignored when the user searched for it, so e.g. "machine" or "microphone" can still find
     * songs with those words in their titles.
     */
    fun isLikelyKaraoke(
        item: SearchResultItem,
        rawQuery: String,
    ): Boolean {
        val duration = item.durationSeconds ?: return false
        if (duration !in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) return false

        val title = normalize(item.title)
        val channel = normalize(item.channelName)
        val queryWords = normalize(rawQuery).split(WHITESPACE).filter { it != "karaoke" }.toSet()

        val equipmentCheckTitle =
            if (SONG_SEPARATOR.containsMatchIn(title)) title.replace(LEADING_KARAOKE, "") else title
        val isBlocked =
            EQUIPMENT_TERMS.any { it.containsMatchIn(equipmentCheckTitle) } ||
                BLOCKED_TERMS.any { (term, pattern) ->
                    val searchedFor = term.split(' ').any { it in queryWords }
                    !searchedFor && pattern.containsMatchIn(title)
                }
        if (isBlocked) return false

        return KARAOKE_MARKERS.any { it in title || it in channel } || channel in KARAOKE_CHANNELS
    }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .trim()
}
