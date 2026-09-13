package com.karalo.feature.search.voice

/** Outcome of a single round-trip through the system speech-recognition activity. */
sealed interface VoiceSearchResult {
    data class Success(val text: String) : VoiceSearchResult

    data object NoMatch : VoiceSearchResult

    data object Cancelled : VoiceSearchResult
}
