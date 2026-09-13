package com.karalo.feature.search.presentation

// Matches the whole word "karaoke" (case-insensitive) plus any surrounding whitespace, so removing
// it doesn't leave a double space or a leading/trailing one behind.
private val KARAOKE_WORD = Regex("""(?i)\s*\bkaraoke\b\s*""")

/**
 * Hides the word "karaoke" from a suggestion's display text — nearly every autocomplete result
 * contains it, and it's redundant noise in a karaoke app. The raw suggestion (unformatted) is
 * still what gets submitted when the suggestion is picked.
 */
internal fun formatSuggestion(suggestion: String): String {
    val cleaned = suggestion.replace(KARAOKE_WORD, " ").trim()
    // Fall back to the raw suggestion if stripping "karaoke" would leave nothing to show (e.g. a
    // suggestion that is just "karaoke" on its own).
    return cleaned.ifEmpty { suggestion }
}
