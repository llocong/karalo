package com.karalo.core.common.session

import com.karalo.core.common.model.PlayableItemRef

/**
 * In-memory (never persisted) hand-off of the most recent search results from `:feature-search`
 * to `:feature-player`, so the player's Next/Previous can walk the queue the user searched
 * without either feature module depending on the other, and without a database.
 *
 * Scoped to the Activity's lifetime (`@ActivityRetainedScoped` binding in `:app`) — cleared on
 * process death, at which point the player falls back to single-video playback.
 */
interface SearchSessionHolder {
    fun setLastResults(items: List<PlayableItemRef>)
    fun getLastResults(): List<PlayableItemRef>
    fun clear()
}
