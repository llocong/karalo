package com.karalo.backend.db

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.PlayHistory
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.plugins.connectDatabase
import com.karalo.backend.plugins.migrateToPlayHistory
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** Finished songs move to play_history (no guest data) and leave queue_items. */
class PlayHistoryTest {
    private val sessionRepository = SessionRepository()
    private val queueRepository = QueueRepository(sessionRepository)
    private val participantRepository = ParticipantRepository()
    private val sessionId: String
    private val guest: com.karalo.backend.domain.model.ParticipantJoinResponseDto

    init {
        val dbFile = File.createTempFile("karalo-history-test-", ".db").apply { deleteOnExit() }
        connectDatabase(AppConfig(dbPath = dbFile.absolutePath))
        sessionId = sessionRepository.ensureSession("history-tv", null).session.id
        guest = participantRepository.join(sessionId, "Alice")
    }

    private fun addSong(title: String) =
        queueRepository.add(
            sessionId = sessionId,
            participantId = guest.participantId,
            participantName = guest.displayName,
            videoId = "abcdefghijk",
            title = title,
            channelName = "Channel",
            thumbnailUrl = null,
            durationSeconds = 120,
        )

    private fun history() =
        transaction {
            PlayHistory.selectAll().orderBy(PlayHistory.playedAt).map {
                Triple(it[PlayHistory.title], it[PlayHistory.playSource], it[PlayHistory.skipped])
            }
        }

    private fun queueRowCount() = transaction { QueueItems.selectAll().count() }

    @Test
    fun `consumeNext moves the song to history as played or skipped and removes it from the queue`() {
        addSong("First")
        addSong("Second")
        queueRepository.consumeNext(sessionId, skipped = false)
        queueRepository.consumeNext(sessionId, skipped = true)

        assertEquals(listOf(Triple("First", "QUEUE", false), Triple("Second", "QUEUE", true)), history())
        assertEquals(0, queueRowCount())
    }

    @Test
    fun `removing a song deletes its row without adding history`() {
        val item = addSong("Removed")
        queueRepository.delete(sessionId, item.id)
        assertEquals(0, queueRowCount())
        assertEquals(emptyList(), history())
    }

    @Test
    fun `play-now songs are recorded when they end or are replaced by another play-now song`() {
        sessionRepository.playNowStart(sessionId, "aaaaaaaaaaa", "One", "Channel", null, 100)
        sessionRepository.playNowStart(sessionId, "bbbbbbbbbbb", "Two", "Channel", null, 100)
        sessionRepository.playNowEnd(sessionId)
        sessionRepository.playNowEnd(sessionId) // a repeated end must not record twice

        assertEquals(listOf(Triple("One", "PLAY_NOW", false), Triple("Two", "PLAY_NOW", false)), history())
    }

    @Test
    fun `the startup migration moves old finished rows once and keeps pending ones`() {
        val pending = addSong("Pending")
        transaction {
            listOf("PLAYED" to "Old played", "SKIPPED" to "Old skipped", "REMOVED" to "Old removed").forEachIndexed { i, (status, title) ->
                QueueItems.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[QueueItems.sessionId] = this@PlayHistoryTest.sessionId
                    it[position] = 100L + i
                    it[videoId] = "abcdefghijk"
                    it[QueueItems.title] = title
                    it[channelName] = "Channel"
                    it[addedByParticipantId] = guest.participantId
                    it[addedByDisplayName] = guest.displayName
                    it[QueueItems.status] = status
                    it[createdAt] = Instant.now()
                    it[playedAt] = if (status == "REMOVED") null else Instant.now()
                }
            }
        }

        transaction { migrateToPlayHistory() }
        transaction { migrateToPlayHistory() }

        assertEquals(
            setOf(Triple("Old played", "QUEUE", false), Triple("Old skipped", "QUEUE", true)),
            history().toSet(),
        )
        assertEquals(listOf(pending.id), transaction { QueueItems.selectAll().map { it[QueueItems.id] } })
    }
}
