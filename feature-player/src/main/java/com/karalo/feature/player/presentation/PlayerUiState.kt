package com.karalo.feature.player.presentation

import com.karalo.feature.player.domain.PlayableItem

data class PlayerUiState(
    val currentItem: PlayableItem? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val error: String? = null,
)
