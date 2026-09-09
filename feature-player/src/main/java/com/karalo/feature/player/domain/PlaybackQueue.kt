package com.karalo.feature.player.domain

/**
 * The in-memory queue the player's Previous/Next walk — built once from
 * [com.karalo.core.common.session.SearchSessionHolder]'s snapshot of the last search, never
 * persisted or re-fetched.
 */
data class PlaybackQueue(
    val items: List<PlayableItem>,
    val currentIndex: Int,
) {
    val current: PlayableItem? get() = items.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex + 1 in items.indices
    val hasPrevious: Boolean get() = currentIndex - 1 in items.indices

    fun next(): PlaybackQueue = if (hasNext) copy(currentIndex = currentIndex + 1) else this

    fun previous(): PlaybackQueue = if (hasPrevious) copy(currentIndex = currentIndex - 1) else this

    companion object {
        fun singleItem(item: PlayableItem) = PlaybackQueue(items = listOf(item), currentIndex = 0)

        fun empty() = PlaybackQueue(items = emptyList(), currentIndex = 0)
    }
}
