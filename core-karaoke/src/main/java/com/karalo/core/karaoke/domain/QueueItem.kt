package com.karalo.core.karaoke.domain

/**
 * The TV's view of one persistent, backend-owned queue entry — mirrors the backend's
 * `QueueItemDto` field-for-field (see `backend/.../domain/model/Models.kt`) so no information is
 * lost crossing the network boundary. Distinct from `:feature-player`'s `PlayableItem`: that type
 * is the *local, ephemeral* browse-queue item (a Home/Search click), unaware this persistent queue
 * even exists — see [KaraokeRepository]'s own doc for why the two are kept deliberately separate.
 */
data class QueueItem(
    val id: String,
    val position: Long,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val addedByParticipantId: String,
    val addedByDisplayName: String,
    val addedAt: String,
)

enum class NowPlayingSource { QUEUE, PLAY_NOW }

data class NowPlaying(
    val source: NowPlayingSource,
    val queueItemId: String?,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val addedByDisplayName: String?,
)

/**
 * One internally-consistent view of the persistent queue as of the last REST fetch or WS event.
 * Deliberately NOT diffed/merged field-by-field against the previous snapshot -- the queue lives
 * entirely on the backend, so "update" always means "the newest snapshot wins wholesale", the same
 * one-slot-handoff spirit as [com.karalo.core.common.session.SearchSessionHolder] rather than a
 * client-side CRDT.
 */
data class KaraokeQueueSnapshot(
    val playbackState: String,
    val nowPlaying: NowPlaying?,
    val queue: List<QueueItem>,
    // False only for [EMPTY], the placeholder before the backend's first answer. Lets a reader tell
    // "not loaded yet" apart from "loaded, and empty" (equal otherwise, so a StateFlow would
    // swallow the second as a repeat of the first).
    val loaded: Boolean = true,
) {
    companion object {
        val EMPTY = KaraokeQueueSnapshot(playbackState = "IDLE", nowPlaying = null, queue = emptyList(), loaded = false)
    }
}
