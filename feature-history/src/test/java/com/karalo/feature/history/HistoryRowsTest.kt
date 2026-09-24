package com.karalo.feature.history

import com.karalo.core.karaoke.domain.HistoryPlay
import com.karalo.core.karaoke.domain.MostPlayedSong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryRowsTest {
    private val montreal = ZoneId.of("America/Toronto")
    private val today = LocalDate.of(2026, 9, 24)

    private fun play(
        id: String,
        at: String,
        night: String,
        videoId: String = "v$id",
    ) = HistoryPlay(id, videoId, "Song $id", "Channel", null, Instant.parse(at), Instant.parse(night))

    @Test
    fun `a night running past midnight is one group labelled with its start date`() {
        val night = "2026-09-24T01:00:00Z" // 9:00 PM on Sept 23 in Montreal
        val rows =
            rowsByDate(
                listOf(
                    play("3", "2026-09-24T05:30:00Z", night), // 1:30 AM Sept 24
                    play("2", "2026-09-24T03:10:00Z", night),
                    play("1", "2026-09-24T01:00:00Z", night),
                ),
                montreal,
                today,
            )

        assertEquals(listOf("Wednesday, September 23"), rows.filterIsInstance<HistoryRow.Header>().map { it.label })
        assertEquals(4, rows.size)
        assertEquals("1:30 AM", (rows[1] as HistoryRow.Song).detail)
    }

    @Test
    fun `repeat plays stay separate, and each night gets its own heading`() {
        val rows =
            rowsByDate(
                listOf(
                    play("3", "2026-09-24T02:00:00Z", "2026-09-24T02:00:00Z", videoId = "same"),
                    play("2", "2025-12-31T23:00:00Z", "2025-12-31T22:00:00Z", videoId = "same"),
                    play("1", "2025-12-31T22:00:00Z", "2025-12-31T22:00:00Z", videoId = "same"),
                ),
                montreal,
                today,
            )

        assertEquals(
            listOf("Wednesday, September 23", "Wednesday, December 31, 2025"),
            rows.filterIsInstance<HistoryRow.Header>().map { it.label },
        )
        assertEquals(3, rows.filterIsInstance<HistoryRow.Song>().size)
    }

    @Test
    fun `two nights on the same date also show their start time`() {
        val rows =
            rowsByDate(
                listOf(
                    play("2", "2026-09-24T01:00:00Z", "2026-09-24T01:00:00Z"), // 9 PM
                    play("1", "2026-09-23T19:00:00Z", "2026-09-23T19:00:00Z"), // 3 PM, same date
                ),
                montreal,
                today,
            )

        assertEquals(
            listOf("Wednesday, September 23 · from 9:00 PM", "Wednesday, September 23 · from 3:00 PM"),
            rows.filterIsInstance<HistoryRow.Header>().map { it.label },
        )
    }

    @Test
    fun `most played rows get a heading wherever the play count changes`() {
        val rows =
            rowsByPlayCount(
                listOf(
                    MostPlayedSong("a", "Bohemian Rhapsody", "Queen", null, 12),
                    MostPlayedSong("b", "Don't Stop Me Now", "Queen", null, 8),
                    MostPlayedSong("c", "Other", "X", null, 8),
                    MostPlayedSong("d", "Billie Jean", "MJ", null, 1),
                ),
            )

        assertEquals(
            listOf("12 plays", "8 plays", "1 play"),
            rows.filterIsInstance<HistoryRow.Header>().map { it.label },
        )
        assertEquals(7, rows.size)
    }
}
