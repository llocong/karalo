package com.karalo.backend.db

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.PlayHistory
import com.karalo.backend.plugins.backfillNightStartedAt
import com.karalo.backend.plugins.connectDatabase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The TV History page's reads (by date / most played), pause/resume and clear. */
class PlayHistoryRepositoryTest {
    private val sessionRepository = SessionRepository()
    private val queueRepository = QueueRepository(sessionRepository)
    private val participantRepository = ParticipantRepository()
    private val history = PlayHistoryRepository()
    private val sessionId: String

    init {
        val dbFile = File.createTempFile("karalo-history-repo-test-", ".db").apply { deleteOnExit() }
        connectDatabase(AppConfig(dbPath = dbFile.absolutePath))
        sessionId = sessionRepository.ensureSession("history-repo-tv", null).session.id
    }

    private val sep23At9pm = Instant.parse("2026-09-24T01:00:00Z") // 9:00 PM EDT on Sept 23

    private fun play(
        videoId: String,
        at: Instant,
        title: String = "Song $videoId",
    ) = transaction {
        recordPlayed(sessionId, videoId.padEnd(11, 'x'), title, "Channel", null, 180, "QUEUE", false, playedAt = at)
    }

    private fun queueSong(title: String) {
        val guest = participantRepository.join(sessionId, "Alice")
        queueRepository.add(sessionId, guest.participantId, guest.displayName, "abcdefghijk", title, "Channel", null, 120)
    }

    @Test
    fun `a night running past midnight stays one group, and a 2h+ gap starts a new one`() {
        play("a", sep23At9pm)
        play("b", sep23At9pm.plus(Duration.ofMinutes(90)))
        play("c", sep23At9pm.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(20))) // 12:20 AM
        play("d", sep23At9pm.plus(Duration.ofHours(4)).plus(Duration.ofMinutes(30))) // 1:30 AM
        play("e", sep23At9pm.plus(Duration.ofHours(7))) // 4:00 AM, 2h30 after the last one

        val items = history.byDate(sessionId, null, 50).items
        assertEquals(listOf("e", "d", "c", "b", "a"), items.map { it.videoId.take(1) })
        val nights = items.map { it.nightStartedAt }
        assertEquals(sep23At9pm.plus(Duration.ofHours(7)).toString(), nights[0])
        assertEquals(List(4) { sep23At9pm.toString() }, nights.drop(1))
    }

    @Test
    fun `by-date paging returns every play exactly once, newest first, including repeats`() {
        repeat(125) { i -> play(if (i % 3 == 0) "same" else "v$i", sep23At9pm.plus(Duration.ofMinutes(i.toLong()))) }

        val seen = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = history.byDate(sessionId, cursor, 50)
            seen += page.items.map { it.id }
            cursor = page.nextCursor
        } while (cursor != null)

        assertEquals(125, seen.size)
        assertEquals(125, seen.toSet().size)
        val playedAts = history.byDate(sessionId, null, 100).items.map { Instant.parse(it.playedAt) }
        assertEquals(playedAts.sortedDescending(), playedAts)
    }

    @Test
    fun `most played aggregates by video id, orders by count, and leaves individual plays intact`() {
        repeat(3) { play("queen", sep23At9pm.plus(Duration.ofMinutes(it.toLong())), title = "Bohemian Rhapsody") }
        play("mj", sep23At9pm.plus(Duration.ofMinutes(10)))
        repeat(2) { play("abba", sep23At9pm.plus(Duration.ofMinutes(20L + it))) }

        val page = history.mostPlayed(sessionId, 0, 2)
        assertEquals(listOf("queen" to 3, "abba" to 2), page.items.map { it.videoId.trimEnd('x') to it.playCount })
        assertEquals("Bohemian Rhapsody", page.items.first().title)
        assertEquals(2, page.nextOffset)

        val rest = history.mostPlayed(sessionId, 2, 2)
        assertEquals(listOf(1), rest.items.map { it.playCount })
        assertNull(rest.nextOffset)
        assertEquals(6, history.byDate(sessionId, null, 50).items.size)
    }

    @Test
    fun `clear removes every play`() {
        play("a", sep23At9pm)
        play("b", sep23At9pm.plus(Duration.ofMinutes(5)))
        history.clear(sessionId)
        assertTrue(history.byDate(sessionId, null, 50).items.isEmpty())
    }

    @Test
    fun `songs played while paused are not recorded, and resuming never adds them later`() {
        queueSong("Before")
        queueSong("Paused mid-song")
        queueSong("Started while paused")
        queueSong("After resume")

        queueRepository.consumeNext(sessionId, skipped = false) // "Before" recorded; "Paused mid-song" starts
        assertTrue(history.setPaused(sessionId, true))
        queueRepository.consumeNext(sessionId, skipped = false) // ends while paused; "Started while paused" starts
        assertFalse(history.setPaused(sessionId, false))
        queueRepository.consumeNext(sessionId, skipped = false) // started while paused: stays out
        queueRepository.consumeNext(sessionId, skipped = false) // started after resume: recorded

        val page = history.byDate(sessionId, null, 50)
        assertEquals(listOf("After resume", "Before"), page.items.map { it.title })
        assertFalse(page.paused)
    }

    @Test
    fun `a play-now song started while paused is not recorded`() {
        history.setPaused(sessionId, true)
        sessionRepository.playNowStart(sessionId, "aaaaaaaaaaa", "Paused one", "Channel", null, null)
        history.setPaused(sessionId, false)
        sessionRepository.playNowStart(sessionId, "bbbbbbbbbbb", "Active one", "Channel", null, null)
        sessionRepository.playNowEnd(sessionId)

        assertEquals(listOf("Active one"), history.byDate(sessionId, null, 50).items.map { it.title })
    }

    @Test
    fun `the backfill assigns nights to older rows`() {
        play("a", sep23At9pm)
        play("b", sep23At9pm.plus(Duration.ofHours(1)))
        play("c", sep23At9pm.plus(Duration.ofHours(5)))
        transaction { PlayHistory.update({ PlayHistory.sessionId eq sessionId }) { it[nightStartedAt] = null } }

        transaction { backfillNightStartedAt() }
        transaction { backfillNightStartedAt() }

        val nights =
            transaction {
                PlayHistory.selectAll().orderBy(PlayHistory.playedAt).map { it[PlayHistory.nightStartedAt] }
            }
        assertEquals(listOf(sep23At9pm, sep23At9pm, sep23At9pm.plus(Duration.ofHours(5))), nights)
    }
}
