package com.karalo.backend.db

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.Participants
import com.karalo.backend.domain.ApiException
import com.karalo.backend.plugins.connectDatabase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Lazy removal of guests with no meaningful activity for 2h and no song left in the queue. */
class InactiveGuestCleanupTest {
    private val sessionRepository = SessionRepository()
    private val queueRepository = QueueRepository(sessionRepository)
    private val participantRepository = ParticipantRepository()
    private val sessionId: String
    private val sessionCode: String

    init {
        val dbFile = File.createTempFile("karalo-cleanup-test-", ".db").apply { deleteOnExit() }
        connectDatabase(AppConfig(dbPath = dbFile.absolutePath))
        val session = sessionRepository.ensureSession("cleanup-tv", null).session
        sessionId = session.id
        sessionCode = session.code
    }

    private fun age(
        participantId: String,
        by: Duration,
    ) = transaction {
        Participants.update({ Participants.id eq participantId }) { it[lastActiveAt] = Instant.now().minus(by) }
    }

    private fun lastActiveAt(participantId: String): Instant =
        transaction { Participants.selectAll().where { Participants.id eq participantId }.single()[Participants.lastActiveAt]!! }

    private fun addSong(guest: com.karalo.backend.domain.model.ParticipantJoinResponseDto) =
        queueRepository.add(
            sessionId = sessionId,
            participantId = guest.participantId,
            participantName = guest.displayName,
            videoId = "abcdefghijk",
            title = "Song",
            channelName = "Channel",
            thumbnailUrl = null,
            durationSeconds = 120,
        )

    @Test
    fun `a guest inactive for less than 2 hours is kept`() {
        val guest = participantRepository.join(sessionId, "Alice")
        age(guest.participantId, Duration.ofMinutes(119))
        assertEquals(1, participantRepository.participantCount(sessionId))
    }

    @Test
    fun `a guest inactive for more than 2 hours with no queued song is removed, and their token stops working`() {
        val stale = participantRepository.join(sessionId, "Alice")
        participantRepository.join(sessionId, "Bob")
        age(stale.participantId, Duration.ofMinutes(121))

        assertEquals(1, participantRepository.participantCount(sessionId))
        assertEquals(1, sessionRepository.getPublicSession(sessionCode).participantCount)
        assertFailsWith<ApiException.Unauthorized> { participantRepository.me(sessionId, stale.participantToken) }
    }

    @Test
    fun `a stale guest with a song still in the queue is kept`() {
        val guest = participantRepository.join(sessionId, "Alice")
        addSong(guest)
        age(guest.participantId, Duration.ofHours(5))
        assertEquals(1, participantRepository.participantCount(sessionId))
    }

    @Test
    fun `once their last song has played, a stale guest is removed`() {
        val guest = participantRepository.join(sessionId, "Alice")
        addSong(guest)
        age(guest.participantId, Duration.ofHours(5))
        queueRepository.consumeNext(sessionId, skipped = false)
        assertEquals(0, participantRepository.participantCount(sessionId))
    }

    @Test
    fun `passive calls don't count as activity, meaningful ones do`() {
        val guest = participantRepository.join(sessionId, "Alice")
        age(guest.participantId, Duration.ofHours(1))
        val before = lastActiveAt(guest.participantId)

        participantRepository.me(sessionId, guest.participantToken)
        participantRepository.requireParticipantAuth(sessionId, guest.participantToken)
        assertEquals(before, lastActiveAt(guest.participantId))

        participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true)
        assertTrue(lastActiveAt(guest.participantId) > before)

        age(guest.participantId, Duration.ofHours(1))
        val beforeRename = lastActiveAt(guest.participantId)
        participantRepository.rename(sessionId, guest.participantToken, "Alicia")
        assertTrue(lastActiveAt(guest.participantId) > beforeRename)
    }

    @Test
    fun `a removed guest can join again as a new guest`() {
        val stale = participantRepository.join(sessionId, "Alice")
        age(stale.participantId, Duration.ofHours(3))
        assertEquals(0, participantRepository.participantCount(sessionId))

        val again = participantRepository.join(sessionId, "Alice")
        assertEquals(1, participantRepository.participantCount(sessionId))
        assertEquals("Alice", participantRepository.me(sessionId, again.participantToken).displayName)
    }
}
