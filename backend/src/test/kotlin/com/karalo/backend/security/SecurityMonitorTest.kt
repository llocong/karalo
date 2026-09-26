package com.karalo.backend.security

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityMonitorTest {
    private var now = Instant.parse("2026-10-24T00:31:00Z")
    private val sent = mutableListOf<String>()

    private fun monitor(alerts: Boolean = true) =
        SecurityMonitor(
            hmacKey = ByteArray(32) { 7 },
            alerts = if (alerts) AlertSink { _, title, message -> sent += "$title: $message" } else null,
            sessionCodeOf = { "K7QM4XPZ" },
            clock = { now },
        )

    private fun tick(seconds: Long) {
        now = now.plusSeconds(seconds)
    }

    @Test
    fun `ten joins within 30 seconds are a join burst, and later ones add to it`() {
        val m = monitor()
        repeat(9) { i ->
            m.onJoin("session-1", "198.51.100.$i")
            tick(2)
        }
        assertTrue(m.openEvents().isEmpty())

        m.onJoin("session-1", "198.51.100.9")
        val burst = m.openEvents().single()
        assertEquals(SecurityEventType.JOIN_BURST, burst.type)
        assertEquals(10, burst.count)
        assertEquals("sent", burst.alert)
        assertNull(burst.source, "joins came from many sources")
        assertEquals(listOf("Karalo: Join burst: 10 guests joined K7QM4XPZ in 18 s · K7QM4XPZ"), sent)

        tick(5)
        m.onJoin("session-1", "198.51.100.10")
        assertEquals(11, m.openEvents().single().count)
    }

    @Test
    fun `joins spread out over time aren't a burst`() {
        val m = monitor()
        repeat(20) { i ->
            m.onJoin("session-1", "198.51.100.$i")
            tick(31)
        }
        assertTrue(m.openEvents().isEmpty())
    }

    @Test
    fun `rate-limit hits from one source are one event, alerted from the tenth`() {
        val m = monitor()
        repeat(9) { m.onRateLimited("search", "203.0.113.5", "session-1") }
        assertEquals("none", m.openEvents().single().alert)
        m.onRateLimited("search", "203.0.113.5", "session-1")
        assertEquals("sent", m.openEvents().single().alert)
        assertEquals(10, m.openEvents().single().count)

        // Another source or another limit is its own event.
        m.onRateLimited("search", "203.0.113.6", "session-1")
        m.onRateLimited("queueAdd", "203.0.113.5", "session-1")
        assertEquals(3, m.openEvents().size)
    }

    @Test
    fun `a second alert of the same type within 15 minutes is throttled`() {
        val m = monitor()
        repeat(3) { m.onRejectedTv(wrongKey = true, "203.0.113.1", null) }
        tick(60)
        repeat(3) { m.onRejectedTv(wrongKey = true, "203.0.113.2", null) }
        assertEquals(listOf("sent", "throttled"), m.openEvents().map { it.alert })
        assertEquals(1, sent.size)

        tick(Duration.ofMinutes(16).seconds)
        repeat(3) { m.onRejectedTv(wrongKey = true, "203.0.113.3", null) }
        assertEquals("sent", m.openEvents().last().alert)
    }

    @Test
    fun `without an alert destination, events are only recorded`() {
        val m = monitor(alerts = false)
        repeat(12) { m.onRateLimited("join", "203.0.113.5", "K7QM4XPZ") }
        assertEquals("none", m.openEvents().single().alert)
    }

    @Test
    fun `ten unknown session codes from one source within 10 minutes are code guessing`() {
        val m = monitor()
        repeat(9) {
            m.onUnknownSessionCode("203.0.113.8")
            tick(30)
        }
        assertTrue(m.openEvents().isEmpty())
        m.onUnknownSessionCode("203.0.113.8")
        val guess = m.openEvents().single()
        assertEquals(SecurityEventType.CODE_GUESSING, guess.type)
        assertEquals(10, guess.count)
    }

    @Test
    fun `a search surge far above the usual rate is a spike`() {
        val m = monitor()
        // Quiet for 40 minutes: about one search a minute.
        repeat(40) {
            m.onSearch()
            tick(60)
        }
        // The last 5 minutes already hold 4 of those; 55 more make 59, just under the minimum.
        repeat(55) { m.onSearch() }
        assertTrue(m.openEvents().isEmpty())
        m.onSearch()
        assertEquals(SecurityEventType.SEARCH_SPIKE, m.openEvents().single().type)
    }

    @Test
    fun `right after a restart only a very large surge is a spike`() {
        val m = monitor()
        repeat(299) { m.onSearch() }
        assertTrue(m.openEvents().isEmpty())
        m.onSearch()
        assertEquals(SecurityEventType.SEARCH_SPIKE, m.openEvents().single().type)
    }

    @Test
    fun `busy but steady searching isn't a spike`() {
        val m = monitor()
        // 30 searches a minute for an hour, then 60 in one minute: only twice the usual rate.
        repeat(60) {
            repeat(30) { m.onSearch() }
            tick(60)
        }
        repeat(60) { m.onSearch() }
        assertTrue(m.openEvents().isEmpty())
    }

    @Test
    fun `sources are stable for an address, differ between addresses, and are only a hash`() {
        val m = monitor()
        assertEquals(m.sourceOf("203.0.113.5"), m.sourceOf("203.0.113.5"))
        assertNotEquals(m.sourceOf("203.0.113.5"), m.sourceOf("203.0.113.6"))
        assertEquals(16, m.sourceOf("203.0.113.5").length)
        assertTrue(Regex("^[0-9a-f]{16}$").matches(m.sourceOf("203.0.113.5")))
    }

    @Test
    fun `events are described in plain words`() {
        val d = SecurityMonitor::describe
        assertEquals("Search limit hit 37 times", d(SecurityEventType.RATE_LIMITED, 37, Duration.ZERO, null, "search"))
        assertEquals("48 unknown session codes looked up from one source", d(SecurityEventType.CODE_GUESSING, 48, Duration.ZERO, null, null))
        assertEquals("A TV connected as tv-81c2… with the wrong secret", d(SecurityEventType.BAD_TV_SECRET, 5, Duration.ZERO, null, "tv-81c2aa"))
        assertEquals("A phone used an invalid guest token 6 times", d(SecurityEventType.INVALID_TOKEN, 6, Duration.ZERO, null, null))
        assertEquals("Searches 5× the usual rate", d(SecurityEventType.SEARCH_SPIKE, 80, Duration.ZERO, null, "5"))
        assertEquals("12 guests joined K7QM4XPZ in 30 s", d(SecurityEventType.JOIN_BURST, 12, Duration.ofSeconds(30), "K7QM4XPZ", null))
    }

    @Test
    fun `alert destinations are shown partly hidden`() {
        assertEquals("ntfy.sh/karalo-admin-••••••", AlertSender.masked("https://ntfy.sh/karalo-admin-x7f3k2q9p1z8w5"))
    }
}
