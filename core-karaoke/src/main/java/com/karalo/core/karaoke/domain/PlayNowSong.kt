package com.karalo.core.karaoke.domain

/** The TV's own manual Home/Search selection, reported to the backend so it can mark
 *  now-playing as PLAY_NOW (leaving the persistent queue's head untouched) -- see
 *  [KaraokeRepository.playNowStart]'s own doc. */
data class PlayNowSong(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
)
