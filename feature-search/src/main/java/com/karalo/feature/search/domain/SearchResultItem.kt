package com.karalo.feature.search.domain

import androidx.compose.runtime.Immutable

// Explicitly marked stable for Compose's compiler-driven stability inference, rather than relying
// on it inferring this correctly on its own -- this is consumed across module boundaries (by
// core-ui's TvCarousel/FocusableCard), where that inference is least reliable. All fields are
// already primitive/String/Long?, so this only removes doubt, not a real behavior change.
@Immutable
data class SearchResultItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
)
