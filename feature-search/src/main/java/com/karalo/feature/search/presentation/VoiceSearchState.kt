package com.karalo.feature.search.presentation

/** Transient mic-button/listening status -- distinct from [SearchUiState], which models results. */
sealed interface VoiceSearchState {
    data object Idle : VoiceSearchState

    data object Listening : VoiceSearchState

    data class Error(
        val message: String,
    ) : VoiceSearchState
}
