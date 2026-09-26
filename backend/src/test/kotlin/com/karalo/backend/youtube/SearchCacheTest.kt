package com.karalo.backend.youtube

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchCacheTest {
    private class MutableClock(
        var now: Instant = Instant.parse("2026-09-25T12:00:00Z"),
    ) : Clock() {
        override fun instant(): Instant = now

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private val clock = MutableClock()
    private val cache = SearchCache<String>(ttl = Duration.ofMinutes(10), maxEntries = 2, clock = clock)
    private var loads = 0

    private fun get(key: String) = cache.getOrLoad(key) { "$key#${++loads}" }

    @Test
    fun `a repeat within the ttl is served from memory`() {
        assertEquals("pop#1", get("pop"))
        clock.now = clock.now.plus(Duration.ofMinutes(9))
        assertEquals("pop#1", get("pop"))
        assertEquals(1, loads)
    }

    @Test
    fun `an expired entry is searched again`() {
        get("pop")
        clock.now = clock.now.plus(Duration.ofMinutes(11))
        assertEquals("pop#2", get("pop"))
    }

    @Test
    fun `a failed search isn't stored`() {
        assertFailsWith<IllegalStateException> { cache.getOrLoad("pop") { error("YouTube said no") } }
        assertEquals("pop#1", get("pop"))
    }

    @Test
    fun `when full, the oldest entry makes room`() {
        get("a")
        clock.now = clock.now.plusSeconds(1)
        get("b")
        clock.now = clock.now.plusSeconds(1)
        get("c") // evicts "a"
        assertEquals("b#2", get("b"))
        assertEquals("a#4", get("a"))
    }
}
