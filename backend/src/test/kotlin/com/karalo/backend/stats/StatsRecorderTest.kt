package com.karalo.backend.stats

import com.karalo.backend.routes.deviceClass
import com.karalo.backend.routes.referrerHost
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsRecorderTest {
    @Test
    fun `a visitor is counted again on a new day, since the salt changes`() {
        var now = Instant.parse("2026-09-26T20:00:00Z") // 4 p.m. in Quebec
        val recorder = StatsRecorder(clock = { now })

        recorder.visitor("203.0.113.1", "UA")
        recorder.visitor("203.0.113.1", "UA")
        assertEquals(1L, recorder.pendingCount(Metric.VISITOR))

        now = Instant.parse("2026-09-27T05:00:00Z") // 1 a.m. the next day in Quebec
        recorder.visitor("203.0.113.1", "UA")
        assertEquals(2L, recorder.pendingCount(Metric.VISITOR))
    }

    @Test
    fun `days are counted in Quebec time`() {
        // 11 p.m. in Quebec is already the next day in UTC.
        val recorder = StatsRecorder(clock = { Instant.parse("2026-09-27T03:00:00Z") })
        recorder.count(Metric.SONG_PLAYED)
        assertEquals(setOf(LocalDate.parse("2026-09-26")), recorder.pendingDays())
    }

    @Test
    fun `referrers keep only the site, and Karalo's own pages are direct visits`() {
        assertEquals("google.com", referrerHost("https://www.google.com/search?q=karaoke"))
        assertEquals("facebook.com", referrerHost("https://facebook.com/some/post"))
        assertEquals("", referrerHost("https://karalo.app/privacy.html"))
        assertEquals("", referrerHost(""))
        assertEquals("", referrerHost("not a url"))
    }

    @Test
    fun `devices are classed from the user agent`() {
        assertEquals("phone", deviceClass("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Mobile/15E148"))
        assertEquals("phone", deviceClass("Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/129.0 Mobile Safari/537.36"))
        assertEquals("tablet", deviceClass("Mozilla/5.0 (Linux; Android 14; SM-X910) Chrome/129.0 Safari/537.36"))
        assertEquals("tablet", deviceClass("Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X)"))
        assertEquals("desktop", deviceClass("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15"))
    }
}
