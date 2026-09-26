package com.karalo.backend.youtube

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps recent YouTube search results for a short while. Every guest's playlists run the same
 * handful of queries, and each search is a live scrape through this server's one IP (slow, and
 * what YouTube rate-limits), so repeats within [ttl] are answered from memory. Only successful
 * searches are stored; at [maxEntries] the oldest entry makes room.
 */
internal class SearchCache<T>(
    private val ttl: Duration = Duration.ofMinutes(10),
    private val maxEntries: Int = 500,
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Entry<T>(
        val storedAt: Instant,
        val value: T,
    )

    private val entries = ConcurrentHashMap<String, Entry<T>>()

    fun getOrLoad(
        key: String,
        load: () -> T,
    ): T {
        val now = clock.instant()
        entries[key]?.takeIf { it.storedAt.plus(ttl).isAfter(now) }?.let { return it.value }
        val value = load()
        if (entries.size >= maxEntries) {
            entries.entries.removeIf { it.value.storedAt.plus(ttl).isBefore(now) }
            if (entries.size >= maxEntries) entries.entries.minByOrNull { it.value.storedAt }?.let { entries.remove(it.key) }
        }
        entries[key] = Entry(now, value)
        return value
    }
}
