package com.karalo.feature.player.domain

data class PlayableItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
)
