package com.karalo.backend.db

import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.TokenGenerator
import com.karalo.backend.domain.model.MeDto
import com.karalo.backend.domain.model.ParticipantJoinResponseDto
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
     */
    fun requireParticipantAuth(
        sessionId: String,
        presentedToken: String?,
        markActive: Boolean = false,
    ): Pair<String, String> =
        transaction {
            if (presentedToken == null) throw ApiException.Unauthorized("Missing participant token")
            val candidates = Participants.selectAll().where { Participants.sessionId eq sessionId }
            val match =
                candidates.firstOrNull { row ->
                    TokenGenerator.matches(presentedToken, row[Participants.participantTokenHash])
                } ?: throw ApiException.Unauthorized("Invalid participant token")
            val now = Instant.now()
            Participants.update({ Participants.id eq match[Participants.id] }) {
                it[lastSeenAt] = now
                if (markActive) it[lastActiveAt] = now
            }
            match[Participants.id] to match[Participants.displayName]
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

    /** Accepts either a participant token OR the owning TV's secret — used by the shared queue-read endpoint. */
    fun isValidParticipantOrTv(
        sessionId: String,
        presentedToken: String?,
        sessionRepository: SessionRepository,
    ): Boolean {
        if (presentedToken == null) return false
        val validParticipant =
            runCatching { requireParticipantAuth(sessionId, presentedToken) }.isSuccess
        if (validParticipant) return true
        return runCatching { sessionRepository.requireTvAuth(sessionId, presentedToken) }.isSuccess
    }

    fun participantCount(sessionId: String): Int = transaction { activeParticipantCount(sessionId) }
}

/**
 * Lazily removes guests with no meaningful activity for [GUEST_INACTIVITY_TIMEOUT] and no song
 * left in the queue -- run whenever the guest count is read, rather than by a background job. A
 * single DELETE that matches nothing unless someone really is stale. The NOT EXISTS covers *any*
 * queue_items row, not just PENDING ones, so this can never trip that table's foreign key (played
 * and removed songs leave queue_items altogether -- see [recordPlayed] and [QueueRepository.delete]).
 *
 * A removed guest's token stops working; the phone's web app transparently re-joins under the
 * same name on the resulting 401 (see apiFetch in shared.js), so they simply count as new again.
 * Must be called inside a transaction.
 */
internal fun pruneInactive(sessionId: String) {
    val cutoff = Instant.now().minus(GUEST_INACTIVITY_TIMEOUT)
    Participants.deleteWhere {
        (Participants.sessionId eq sessionId) and
            (lastActiveAt less cutoff) and
            notExists(QueueItems.select(QueueItems.id).where { QueueItems.addedByParticipantId eq Participants.id })
    }
}

/** The session's guest count, after [pruneInactive]. Must be called inside a transaction. */
internal fun activeParticipantCount(sessionId: String): Int {
    pruneInactive(sessionId)
    return Participants.selectAll().where { Participants.sessionId eq sessionId }.count().toInt()
}
