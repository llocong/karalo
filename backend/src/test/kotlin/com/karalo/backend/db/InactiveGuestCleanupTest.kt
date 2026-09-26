package com.karalo.backend.db

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.db.tables.TvInstallations
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.GUEST_EXPIRED
import com.karalo.backend.domain.SESSION_ENDED
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

/**
 * Lazy removal of guests with no meaningful activity for 2h and no song left in the queue, and of
 * whole sessions whose TV went quiet for 30 minutes.
 */
class InactiveGuestCleanupTest {
    private val sessionRepository = SessionRepository()
    private val queueRepository = QueueRepository(sessionRepository)
    private val participantRepository = ParticipantRepository()
    private val sessionId: String
    private val sessionCode: String
    private val tvSecret: String

    init {
        val dbFile = File.createTempFile("karalo-cleanup-test-", ".db").apply { deleteOnExit() }
        connectDatabase(AppConfig(dbPath = dbFile.absolutePath))
        val ensured = sessionRepository.ensureSession("cleanup-tv", null)
        tvSecret = ensured.tvSecret!!
        sessionId = ensured.session.id
        sessionCode = ensured.session.code
    }

    private fun ageTv(by: Duration) =
        transaction { TvInstallations.update({ TvInstallations.id eq "cleanup-tv" }) { it[lastSeenAt] = Instant.now().minus(by) } }

    /** The TV's live connection dropped [by] ago, and the TV hasn't called since. */
    private fun disconnectTv(by: Duration) {
        ageTv(by.plusSeconds(1))
        transaction { Sessions.update({ Sessions.id eq sessionId }) { it[tvDisconnectedAt] = Instant.now().minus(by) } }
    }

    private fun queuedSongs(): Long = transaction { QueueItems.selectAll().where { QueueItems.sessionId eq sessionId }.count() }

    private fun assertRejectedWith(
        code: String,
        block: () -> Unit,
    ) = assertEquals(code, assertFailsWith<ApiException.Unauthorized> { block() }.code)

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
        assertRejectedWith(GUEST_EXPIRED) { participantRepository.me(sessionId, stale.participantToken) }
    }

    @Test
    fun `a phone left open overnight can't act again, even if nothing ran the cleanup`() {
        val guest = participantRepository.join(sessionId, "Ly")
        age(guest.participantId, Duration.ofHours(9))
        val before = lastActiveAt(guest.participantId)

        // Adding a song or searching is a meaningful action: it must not bring the guest back.
        assertRejectedWith(GUEST_EXPIRED) { participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true) }
        assertEquals(before, lastActiveAt(guest.participantId))
        // ...and the reason sticks on every later call.
        assertRejectedWith(GUEST_EXPIRED) { participantRepository.me(sessionId, guest.participantToken) }
        assertEquals(0, participantRepository.participantCount(sessionId))
    }

    @Test
    fun `a stale guest with a song still in the queue can keep acting`() {
        val guest = participantRepository.join(sessionId, "Alice")
        addSong(guest)
        age(guest.participantId, Duration.ofHours(5))
        participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true)
        assertEquals(1, participantRepository.participantCount(sessionId))
    }

    @Test
    fun `tombstones are deleted for good after a week`() {
        val guest = participantRepository.join(sessionId, "Alice")
        age(guest.participantId, Duration.ofHours(3))
        participantRepository.participantCount(sessionId)
        transaction { Participants.update({ Participants.id eq guest.participantId }) { it[removedAt] = Instant.now().minus(Duration.ofDays(8)) } }
        participantRepository.participantCount(sessionId)
        assertEquals(0L, transaction { Participants.selectAll().count() })
    }

    @Test
    fun `a TV quiet for under 30 minutes keeps its session going`() {
        val guest = participantRepository.join(sessionId, "Alice")
        ageTv(Duration.ofMinutes(29))
        participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true)
        assertEquals(false, sessionRepository.getPublicSession(sessionCode).ended)
    }

    @Test
    fun `a TV quiet for 30 minutes ends the session - guests and queue are gone`() {
        val guest = participantRepository.join(sessionId, "Alice")
        addSong(guest) // a queued song doesn't keep anyone in an ended session
        ageTv(Duration.ofMinutes(31))

        assertRejectedWith(SESSION_ENDED) { participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true) }
        assertEquals(0L, queuedSongs())
        assertEquals(0, participantRepository.participantCount(sessionId))
        assertEquals(true, sessionRepository.getPublicSession(sessionCode).ended)
        assertRejectedWith(SESSION_ENDED) { sessionRepository.resolveSessionIdForCode(sessionCode) }
    }

    @Test
    fun `the join page is the first to notice an ended session`() {
        participantRepository.join(sessionId, "Alice")
        ageTv(Duration.ofMinutes(45))
        val public = sessionRepository.getPublicSession(sessionCode)
        assertEquals(true, public.ended)
        assertEquals(0, public.participantCount)
    }

    @Test
    fun `when the TV comes back it starts fresh - old guests stay out, new ones can join`() {
        val old = participantRepository.join(sessionId, "Alice")
        ageTv(Duration.ofHours(10))

        val restarted = sessionRepository.ensureSession("cleanup-tv", tvSecret).session
        assertEquals(sessionCode, restarted.code)
        assertEquals(0, restarted.participantCount)
        assertEquals(false, sessionRepository.getPublicSession(sessionCode).ended)
        assertRejectedWith(SESSION_ENDED) { participantRepository.me(sessionId, old.participantToken) }

        val fresh = participantRepository.join(sessionRepository.resolveSessionIdForCode(sessionCode), "Alice")
        assertEquals("Alice", participantRepository.me(sessionId, fresh.participantToken).displayName)
    }

    @Test
    fun `closing the TV app ends the session once its connection has been gone for 2 minutes`() {
        val guest = participantRepository.join(sessionId, "Ly")
        disconnectTv(Duration.ofMinutes(3))
        assertRejectedWith(SESSION_ENDED) { participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true) }
    }

    @Test
    fun `a TV connection that dropped under 2 minutes ago keeps the session going`() {
        val guest = participantRepository.join(sessionId, "Ly")
        disconnectTv(Duration.ofSeconds(90))
        participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true)
    }

    @Test
    fun `a TV call after its connection dropped keeps the session going`() {
        val guest = participantRepository.join(sessionId, "Ly")
        disconnectTv(Duration.ofMinutes(3))
        ageTv(Duration.ofMinutes(1)) // e.g. its heartbeat, while the connection is still down
        participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true)
    }

    @Test
    fun `the TV reconnecting clears the drop`() {
        val guest = participantRepository.join(sessionId, "Ly")
        disconnectTv(Duration.ofMinutes(1))
        sessionRepository.setTvConnected(sessionId, connected = true)
        ageTv(Duration.ofMinutes(10)) // well past the grace, but connected again
        participantRepository.requireParticipantAuth(sessionId, guest.participantToken, markActive = true)
    }

    @Test
    fun `any TV call counts as a sign of life`() {
        participantRepository.join(sessionId, "Alice")
        ageTv(Duration.ofMinutes(25))
        sessionRepository.requireTvAuth(sessionId, tvSecret)
        val tvLastSeen = transaction { TvInstallations.selectAll().single()[TvInstallations.lastSeenAt] }
        assertTrue(tvLastSeen > Instant.now().minus(Duration.ofMinutes(1)))
        assertEquals(1, participantRepository.participantCount(sessionId))
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
