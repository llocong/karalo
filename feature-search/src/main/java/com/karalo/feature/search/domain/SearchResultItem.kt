package com.karalo.feature.search.domain

data class SearchResultItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
)
