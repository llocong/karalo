package com.karalo.feature.player.domain

data class PlayableStream(
    val uri: String,
    val mimeType: String?,
    val isAdaptive: Boolean,
)
