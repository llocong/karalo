package com.karalo.core.common.model

/**
 * Minimal, cross-feature-safe reference to a playable video. `:feature-search` and
 * `:feature-player` each map their own richer domain models to/from this type so neither module
 * needs to depend on the other — see [com.karalo.core.common.session.SearchSessionHolder].
 */
data class PlayableItemRef(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long?,
)
