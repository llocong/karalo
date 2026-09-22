package com.karalo.feature.player.domain

/**
 * Picks a single starting item out of a list — built once from
 * [com.karalo.core.common.session.SearchSessionHolder]'s snapshot of the last search, never
 * persisted or re-fetched. There is no browsing UI over [items]/[currentIndex] beyond that initial
 * pick: the player's Next button advances the persistent remote queue instead (see
 * [com.karalo.feature.player.presentation.PlayerViewModel.next]), and there is no "Previous"
 * concept in that remote queue at all.
 */
data class PlaybackQueue(
    val items: List<PlayableItem>,
    val currentIndex: Int,
) {
    val current: PlayableItem? get() = items.getOrNull(currentIndex)

    companion object {
        fun singleItem(item: PlayableItem) = PlaybackQueue(items = listOf(item), currentIndex = 0)

        fun empty() = PlaybackQueue(items = emptyList(), currentIndex = 0)
    }
}
