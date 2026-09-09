package com.karalo.youtubeclient.model

data class YtVideoSummary(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    /** Null/negative means unknown or live (NewPipeExtractor reports -1 for those). */
    val durationSeconds: Long?,
)

data class YtSuggestion(val text: String)

/**
 * A resolved, playable source for a video. [isAdaptive] distinguishes a single progressive
 * (muxed audio+video) file — simplest for ExoPlayer, preferred when available — from a DASH/HLS
 * manifest URL that ExoPlayer must adapt.
 */
data class YtStreamInfo(
    val playbackUrl: String,
    val mimeType: String?,
    val isAdaptive: Boolean,
)
