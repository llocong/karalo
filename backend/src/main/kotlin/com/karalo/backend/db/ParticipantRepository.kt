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
import java.time.Instant
import java.util.UUID

private val CONTROL_CHARS = Regex("\\p{Cntrl}")

class ParticipantRepository {
    fun join(
        sessionId: String,
        rawDisplayName: String,
    ): ParticipantJoinResponseDto {
        val displayName = rawDisplayName.replace(CONTROL_CHARS, "").trim()
        if (displayName.isEmpty() || displayName.length > 40) {
            throw ApiException.Validation("displayName must be 1-40 characters")
        }
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
