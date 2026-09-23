package com.karalo.backend.db

import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.TokenGenerator
import com.karalo.backend.domain.model.MeDto
import com.karalo.backend.domain.model.ParticipantJoinResponseDto
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.text.Normalizer
import java.time.Instant
import java.util.UUID

// Control characters, plus invisible "format" characters (zero-width spaces, bidirectional
// overrides like U+202E that make text render reversed, BOMs...) that can make one name look like
// another or garble whatever is displayed next to it. U+200D (zero-width joiner) is the one format
// character kept, since multi-person/profession emoji are built from it.
private val INVISIBLE_CHARS = Regex("[\\p{Cc}\\p{Cf}&&[^\\u200D]]")
private val WHITESPACE_RUNS = Regex("[\\s\\p{Z}]+")
private val FORBIDDEN_CHARS = Regex("[<>]")

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
            }
            ParticipantJoinResponseDto(
                participantId = participantId,
                participantToken = rawToken,
                sessionId = sessionId,
                displayName = displayName,
            )
        }
    }

    /** Returns the authenticated participant's id/name, or throws Unauthorized. */
    fun requireParticipantAuth(
        sessionId: String,
        presentedToken: String?,
    ): Pair<String, String> =
        transaction {
            if (presentedToken == null) throw ApiException.Unauthorized("Missing participant token")
            val candidates = Participants.selectAll().where { Participants.sessionId eq sessionId }
            val match =
                candidates.firstOrNull { row ->
                    TokenGenerator.matches(presentedToken, row[Participants.participantTokenHash])
                } ?: throw ApiException.Unauthorized("Invalid participant token")
            Participants.update({ Participants.id eq match[Participants.id] }) { it[lastSeenAt] = Instant.now() }
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
            val (participantId, _) = requireParticipantAuth(sessionId, presentedToken)
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

    fun participantCount(sessionId: String): Int =
        transaction {
            Participants.selectAll().where { Participants.sessionId eq sessionId }.count().toInt()
        }

    fun exists(
        sessionId: String,
        participantId: String,
    ): Boolean =
        transaction {
            !Participants.selectAll().where { (Participants.sessionId eq sessionId) and (Participants.id eq participantId) }.empty()
        }
}
