package com.karalo.core.karaoke.domain

import java.time.Instant

/** One play of a song, as recorded in the session's history. Plays are never merged. */
data class HistoryPlay(
    val id: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val playedAt: Instant,
    /** Start of the karaoke night this play belongs to (see the backend's KARAOKE_NIGHT_GAP). */
    val nightStartedAt: Instant,
)

/** A video with its total number of plays, for the History page's "Most played" sort. */
data class MostPlayedSong(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val playCount: Int,
)

/**
 * One page of history plus whether recording is paused. [next] is what to pass back for the
 * following page (a cursor or an offset), or null once the end is reached.
 */
data class HistoryPage<T, K>(
    val items: List<T>,
    val next: K?,
    val paused: Boolean,
)
