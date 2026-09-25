package com.karalo.backend.db

import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.GUEST_EXPIRED
import com.karalo.backend.domain.SESSION_ENDED
import com.karalo.backend.domain.TokenGenerator
import com.karalo.backend.domain.model.MeDto
import com.karalo.backend.domain.model.ParticipantJoinResponseDto
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Control characters, plus invisible "format" characters (zero-width spaces, bidirectional
// overrides like U+202E that make text render reversed, BOMs...) that can make one name look like
// another or garble whatever is displayed next to it. U+200D (zero-width joiner) is the one format
// character kept, since multi-person/profession emoji are built from it.
private val INVISIBLE_CHARS = Regex("[\\p{Cc}\\p{Cf}&&[^\\u200D]]")
private val WHITESPACE_RUNS = Regex("[\\s\\p{Z}]+")
private val FORBIDDEN_CHARS = Regex("[<>]")

/** How long a guest can go without a meaningful action before [pruneInactive] may remove them. */
internal val GUEST_INACTIVITY_TIMEOUT: Duration = Duration.ofHours(2)

/** Same limit (and same rules) as the mobile web's join form and its "Change your name" modal. */
internal const val DISPLAY_NAME_MAX_LENGTH = 16

/**
 * Pure (top-level, not a method) so it's unit-testable without a DB transaction -- mirrors why
 * `applyKaraokePrefix` was pulled out the same way. Shared by [ParticipantRepository.join] and
 * [ParticipantRepository.rename] so both apply identical rules; `normalizeDisplayName` in the web
 * app's shared.js mirrors this for immediate feedback, but this is what's actually enforced.
 *
 * Storage is already safe from SQL injection regardless of content (Exposed only ever sends
 * parameterized statements), and every renderer escapes it; rejecting `<`/`>` is defense in depth
 * for any future renderer that forgets to.
 */
internal fun normalizeDisplayName(raw: String): String {
    val displayName =
        Normalizer
            .normalize(raw, Normalizer.Form.NFC)
            .replace(INVISIBLE_CHARS, "")
            .replace(WHITESPACE_RUNS, " ")
            .trim()
    if (displayName.isEmpty() || displayName.length > DISPLAY_NAME_MAX_LENGTH) {
        throw ApiException.Validation("displayName must be 1-$DISPLAY_NAME_MAX_LENGTH characters")
    }
    if (FORBIDDEN_CHARS.containsMatchIn(displayName)) {
        throw ApiException.Validation("displayName can't contain < or >")
    }
    return displayName
}

class ParticipantRepository {
    fun join(
        sessionId: String,
        rawDisplayName: String,
    ): ParticipantJoinResponseDto {
        val displayName = normalizeDisplayName(rawDisplayName)
        return transaction {
            val participantId = UUID.randomUUID().toString()
            val rawToken = TokenGenerator.generate()
            val now = Instant.now()
            Participants.insert {
                it[id] = participantId
                it[Participants.sessionId] = sessionId
                it[Participants.displayName] = displayName
                it[participantTokenHash] = TokenGenerator.hash(rawToken)
                it[createdAt] = now
                it[lastSeenAt] = now
                it[lastActiveAt] = now
            }
            ParticipantJoinResponseDto(
                participantId = participantId,
                participantToken = rawToken,
                sessionId = sessionId,
                displayName = displayName,
            )
        }
    }

    /**
     * Returns the authenticated participant's id/name, or throws Unauthorized. [markActive] is for
     * meaningful actions (adding/removing/reordering songs, playback commands, search, rename) --
     * it also refreshes lastActiveAt, which is what keeps a guest out of [pruneInactive]'s reach.
     * Passive calls (/me, queue snapshot refreshes, the WS connect) leave it alone.
     *
     * Expiry is checked here on every call, not only when cleanup happens to run: a phone left
     * open overnight gets GUEST_EXPIRED (or SESSION_ENDED, if the TV went quiet) on its next
     * request instead of quietly picking up where it left off.
     */
    fun requireParticipantAuth(
        sessionId: String,
        presentedToken: String?,
        markActive: Boolean = false,
    ): Pair<String, String> =
        transaction {
            if (presentedToken == null) throw ApiException.Unauthorized("Missing participant token")
            val now = Instant.now()
            endSessionIfTvInactive(sessionId, now)
            val match =
                Participants.selectAll().where { Participants.sessionId eq sessionId }.firstOrNull { row ->
                    TokenGenerator.matches(presentedToken, row[Participants.participantTokenHash])
                }
            val participantId = match?.get(Participants.id)
            val reason =
                when {
                    match == null -> if (isSessionEnded(sessionId)) SESSION_ENDED else null
                    match[Participants.removedReason] != null -> match[Participants.removedReason]
                    isStale(participantId!!, match[Participants.lastActiveAt], now) -> {
                        tombstone(participantId, GUEST_EXPIRED, now)
                        GUEST_EXPIRED
                    }
                    else -> null
                }
            if (reason != null) {
                commit() // keep any end/expiry just recorded above; the throw rolls back otherwise
                throw ApiException.Unauthorized(if (reason == SESSION_ENDED) "This karaoke session is over" else "Your connection timed out", code = reason)
            }
            if (match == null) throw ApiException.Unauthorized("Invalid participant token")
            Participants.update({ Participants.id eq participantId!! }) {
                it[lastSeenAt] = now
                if (markActive) it[lastActiveAt] = now
            }
            participantId!! to match[Participants.displayName]
        }

    fun me(
        sessionId: String,
        presentedToken: String?,
    ): MeDto {
        val (participantId, displayName) = requireParticipantAuth(sessionId, presentedToken)
        return MeDto(participantId = participantId, displayName = displayName, sessionId = sessionId)
    }

    /**
     * Updates the authenticated participant's own display name -- does NOT retroactively change
     * `addedByDisplayName` on queue items already added under the old name, matching how that
     * column is a denormalized snapshot taken at add-time, not a live reference (see
     * [com.karalo.backend.routes.queueRoutes]).
     */
    fun rename(
        sessionId: String,
        presentedToken: String?,
        rawDisplayName: String,
    ): MeDto {
        val displayName = normalizeDisplayName(rawDisplayName)
        return transaction {
            val (participantId, _) = requireParticipantAuth(sessionId, presentedToken, markActive = true)
            Participants.update({ Participants.id eq participantId }) { it[Participants.displayName] = displayName }
            MeDto(participantId = participantId, displayName = displayName, sessionId = sessionId)
        }
    }

    /**
     * Accepts either a participant token OR the owning TV's secret — used by the shared queue-read
     * endpoint. When neither matches, throws the guest's error, so an expired phone still learns why.
     */
    fun requireParticipantOrTv(
        sessionId: String,
        presentedToken: String?,
        sessionRepository: SessionRepository,
    ) {
        val guestFailure = runCatching { requireParticipantAuth(sessionId, presentedToken) }.exceptionOrNull() ?: return
        if (presentedToken != null && runCatching { sessionRepository.requireTvAuth(sessionId, presentedToken) }.isSuccess) return
        throw guestFailure
    }

    fun participantCount(sessionId: String): Int = transaction { activeParticipantCount(sessionId) }
}

/** How long a removed guest's tombstone is kept, so their old token can still say why it stopped working. */
private val TOMBSTONE_RETENTION: Duration = Duration.ofDays(7)

private fun hasQueuedSong(participantId: String): Boolean =
    !QueueItems.selectAll().where { QueueItems.addedByParticipantId eq participantId }.empty()

/** Past [GUEST_INACTIVITY_TIMEOUT] without a meaningful action, and no song of theirs left in the queue. */
private fun isStale(
    participantId: String,
    lastActiveAt: Instant?,
    now: Instant,
): Boolean = lastActiveAt != null && lastActiveAt.isBefore(now.minus(GUEST_INACTIVITY_TIMEOUT)) && !hasQueuedSong(participantId)

private fun tombstone(
    participantId: String,
    reason: String,
    now: Instant,
) = Participants.update({ Participants.id eq participantId }) {
    it[removedAt] = now
    it[removedReason] = reason
}

/**
 * Lazily removes guests with no meaningful activity for [GUEST_INACTIVITY_TIMEOUT] and no song
 * left in the queue -- run whenever the guest count is read, rather than by a background job
 * ([ParticipantRepository.requireParticipantAuth] checks the same rule for the guest calling, so
 * an expired guest is caught even when nothing has read the count). Removed guests become
 * GUEST_EXPIRED tombstones, which are deleted for good after [TOMBSTONE_RETENTION]. The NOT
 * EXISTS covers *any* queue_items row, not just PENDING ones, so this can never trip that table's
 * foreign key (played and removed songs leave queue_items altogether -- see [recordPlayed] and
 * [QueueRepository.delete]).
 *
 * A removed guest's token stops working: on the resulting 401 the phone's web app shows the
 * "session over" page for its reason (see sendToSessionOver in shared.js).
 * Must be called inside a transaction.
 */
internal fun pruneInactive(sessionId: String) {
    val now = Instant.now()
    val cutoff = now.minus(GUEST_INACTIVITY_TIMEOUT)
    val noQueuedSong = notExists(QueueItems.select(QueueItems.id).where { QueueItems.addedByParticipantId eq Participants.id })
    Participants.update({
        (Participants.sessionId eq sessionId) and Participants.removedAt.isNull() and (Participants.lastActiveAt less cutoff) and noQueuedSong
    }) {
        it[removedAt] = now
        it[removedReason] = GUEST_EXPIRED
    }
    Participants.deleteWhere {
        (Participants.sessionId eq sessionId) and (removedAt less now.minus(TOMBSTONE_RETENTION)) and noQueuedSong
    }
}

/** The session's guest count, after [pruneInactive]. Must be called inside a transaction. */
internal fun activeParticipantCount(sessionId: String): Int {
    pruneInactive(sessionId)
    return Participants.selectAll().where { (Participants.sessionId eq sessionId) and Participants.removedAt.isNull() }.count().toInt()
}
