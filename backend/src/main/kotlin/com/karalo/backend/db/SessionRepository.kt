package com.karalo.backend.db

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.db.tables.TvInstallations
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.SESSION_ENDED
import com.karalo.backend.domain.SessionCodeGenerator
import com.karalo.backend.domain.TokenGenerator
import com.karalo.backend.domain.model.NowPlayingDto
import com.karalo.backend.domain.model.PublicSessionDto
import com.karalo.backend.domain.model.SeasonalTheme
import com.karalo.backend.domain.model.SessionEnsureResponseDto
import com.karalo.backend.domain.model.SessionSummaryDto
import com.karalo.backend.youtube.formatVideoTitle
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Owns TV installation identity + the one-session-per-TV lifecycle. Deliberately combined into one
 * repository rather than a separate TvInstallationRepository: every operation here either creates
 * both rows together (ensure, first call) or validates the TV secret specifically to act on the
 * session it owns — there's no meaningful TV-installation-only operation independent of "the
 * session it maps to" in this MVP.
 */
class SessionRepository(
    /** See [com.karalo.backend.config.AppConfig.tvRegistrationKey]; null keeps registration open. */
    tvRegistrationKey: String? = null,
) {
    private val registrationKeyHash = tvRegistrationKey?.let(TokenGenerator::hash)

    /**
     * The first caller for a brand-new [tvId] claims it and receives the secret; every later call
     * for that [tvId] must present it. When a registration key is configured, claiming a new [tvId] also
     * requires [presentedRegistrationKey] to match it, so only TVs built with the key can register
     * on an internet-exposed server. Without it this is trust-on-first-use, fine on a home LAN.
     */
    fun ensureSession(
        tvId: String,
        presentedSecret: String?,
        presentedRegistrationKey: String? = null,
    ): SessionEnsureResponseDto =
        transaction {
            val existing = TvInstallations.selectAll().where { TvInstallations.id eq tvId }.singleOrNull()
            val now = Instant.now()

            if (existing == null) {
                if (registrationKeyHash != null &&
                    (presentedRegistrationKey == null || !TokenGenerator.matches(presentedRegistrationKey, registrationKeyHash))
                ) {
                    throw ApiException.Unauthorized("Invalid or missing TV registration key")
                }
                val rawSecret = TokenGenerator.generate()
                TvInstallations.insert {
                    it[id] = tvId
                    it[tvSecretHash] = TokenGenerator.hash(rawSecret)
                    it[createdAt] = now
                    it[lastSeenAt] = now
                }
                val sessionId = UUID.randomUUID().toString()
                val code = generateUniqueCode()
                Sessions.insert {
                    it[id] = sessionId
                    it[tvInstallationId] = tvId
                    it[Sessions.code] = code
                    it[queuePositionCursor] = 0
                    it[playbackState] = "IDLE"
                    it[updatedAt] = now
                }
                val session = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
                return@transaction SessionEnsureResponseDto(tvSecret = rawSecret, session = toSummary(session))
            }

            if (presentedSecret == null || !TokenGenerator.matches(presentedSecret, existing[TvInstallations.tvSecretHash])) {
                throw ApiException.Unauthorized("Invalid or missing TV secret")
            }
            val sessionId =
                Sessions.selectAll().where { Sessions.tvInstallationId eq tvId }.singleOrNull()?.get(Sessions.id)
                    ?: throw ApiException.NotFound("Session missing for a known TV installation — data inconsistency")
            // The TV repeats this call every few minutes while it's in the foreground: it's the
            // session's heartbeat, and the first one after a long sleep starts it fresh.
            touchTv(sessionId, tvId, now)
            val session = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
            SessionEnsureResponseDto(tvSecret = null, session = toSummary(session))
        }

    /**
     * Validates a TV bearer secret against whichever installation owns [sessionId]. A valid call
     * is also a sign of life from the TV (see [touchTv]).
     */
    fun requireTvAuth(
        sessionId: String,
        presentedSecret: String?,
    ): Unit =
        transaction {
            val session = Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull() ?: throw ApiException.NotFound("Unknown session")
            val tv =
                TvInstallations
                    .selectAll()
                    .where { TvInstallations.id eq session[Sessions.tvInstallationId] }
                    .single()
            if (presentedSecret == null || !TokenGenerator.matches(presentedSecret, tv[TvInstallations.tvSecretHash])) {
                throw ApiException.Unauthorized("Invalid TV secret")
            }
            touchTv(sessionId, tv[TvInstallations.id])
        }

    fun getPublicSession(rawCode: String): PublicSessionDto =
        transaction {
            val code = SessionCodeGenerator.normalize(rawCode)
            val session = Sessions.selectAll().where { Sessions.code eq code }.singleOrNull() ?: throw ApiException.NotFound("Unknown session code")
            val sessionId = session[Sessions.id]
            endSessionIfTvInactive(sessionId)
            PublicSessionDto(
                sessionId = sessionId,
                code = session[Sessions.code],
                participantCount = activeParticipantCount(sessionId),
                theme = session[Sessions.theme],
                ended = isSessionEnded(sessionId),
            )
        }

    /** For joining: a session that has ended can't take new guests until its TV comes back. */
    fun resolveSessionIdForCode(rawCode: String): String =
        transaction {
            val code = SessionCodeGenerator.normalize(rawCode)
            val sessionId =
                Sessions.selectAll().where { Sessions.code eq code }.singleOrNull()?.get(Sessions.id)
                    ?: throw ApiException.NotFound("Unknown session code")
            endSessionIfTvInactive(sessionId)
            if (isSessionEnded(sessionId)) {
                commit() // keep the end just realized above; the throw below rolls back otherwise
                throw ApiException.Unauthorized("This karaoke session is over", code = SESSION_ENDED)
            }
            sessionId
        }

    fun getPlaybackState(sessionId: String): String =
        transaction {
            Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull()?.get(Sessions.playbackState)
                ?: throw ApiException.NotFound("Unknown session")
        }

    fun getTheme(sessionId: String): String =
        transaction {
            Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull()?.get(Sessions.theme)
                ?: throw ApiException.NotFound("Unknown session")
        }

    /** Stores the session's seasonal theme; rejects anything that isn't a [SeasonalTheme] name. */
    fun setTheme(
        sessionId: String,
        theme: String,
    ): String {
        val valid = SeasonalTheme.entries.firstOrNull { it.name == theme }
            ?: throw ApiException.Validation("theme must be one of ${SeasonalTheme.entries.joinToString()}")
        transaction {
            Sessions.update({ Sessions.id eq sessionId }) {
                it[Sessions.theme] = valid.name
                it[updatedAt] = Instant.now()
            }
        }
        return valid.name
    }

    fun setPlaybackState(
        sessionId: String,
        state: String,
    ) = transaction {
        require(state in listOf("PLAYING", "PAUSED")) { "Invalid playback state" }
        Sessions.update({ Sessions.id eq sessionId }) {
            it[playbackState] = state
            it[updatedAt] = Instant.now()
        }
    }

    fun playNowStart(
        sessionId: String,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        durationSeconds: Int?,
    ): NowPlayingDto =
        transaction {
            // Starting a new Play-Now song over one still playing ends that one (the TV sends no
            // separate end for it), so it gets its history entry here.
            recordPlayNowIfPlaying(sessionId)
            Sessions.update({ Sessions.id eq sessionId }) {
                it[nowPlayingSource] = "PLAY_NOW"
                it[nowPlayingHistorySuppressed] = Sessions.historyPaused
                it[playNowVideoId] = videoId
                it[playNowTitle] = title
                it[playNowChannelName] = channelName
                it[playNowThumbnailUrl] = thumbnailUrl
                it[playNowDurationSeconds] = durationSeconds
                it[playbackState] = "PLAYING"
                it[updatedAt] = Instant.now()
            }
            requireNotNull(resolveNowPlaying(sessionId)) { "Play-Now item just written but not found" }
        }

    /**
     * Play-Now never touched `queuePositionCursor`/the PENDING head — this is precisely what makes
     * "the queue resumes exactly where it left off" true after Play-Now ends, with zero extra
     * bookkeeping: whatever was already the head (if any) is simply what [resolveNowPlaying]
     * reports once `nowPlayingSource` flips back to QUEUE.
     */
    fun playNowEnd(sessionId: String): NowPlayingDto? =
        transaction {
            recordPlayNowIfPlaying(sessionId)
            Sessions.update({ Sessions.id eq sessionId }) {
                it[nowPlayingSource] = "QUEUE"
                it[playNowVideoId] = null
                it[playNowTitle] = null
                it[playNowChannelName] = null
                it[playNowThumbnailUrl] = null
                it[playNowDurationSeconds] = null
                it[updatedAt] = Instant.now()
            }
            syncNowPlayingToQueueHead(sessionId)
            resolveNowPlaying(sessionId)
        }

    /** Adds the current Play-Now song (if one is playing) to play_history. Idempotent per song. */
    private fun recordPlayNowIfPlaying(sessionId: String) {
        val session = Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull() ?: return
        if (session[Sessions.nowPlayingSource] != "PLAY_NOW") return
        if (session[Sessions.nowPlayingHistorySuppressed]) return
        val videoId = session[Sessions.playNowVideoId] ?: return
        recordPlayed(
            sessionId = sessionId,
            videoId = videoId,
            title = session[Sessions.playNowTitle].orEmpty(),
            channelName = session[Sessions.playNowChannelName].orEmpty(),
            thumbnailUrl = session[Sessions.playNowThumbnailUrl],
            durationSeconds = session[Sessions.playNowDurationSeconds],
            source = "PLAY_NOW",
            skipped = false,
        )
    }

    /**
     * Points `nowPlayingQueueItemId` at whatever the current PENDING head is (or clears it). A
     * different song becoming now-playing counts as that song starting, for the history switch
     * (see Sessions.nowPlayingHistorySuppressed).
     */
    internal fun syncNowPlayingToQueueHead(sessionId: String) {
        val session = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
        val head =
            QueueItems
                .selectAll()
                .where { (QueueItems.sessionId eq sessionId) and (QueueItems.status eq "PENDING") }
                .orderBy(QueueItems.position, SortOrder.ASC)
                .limit(1)
                .singleOrNull()
        val headId = head?.get(QueueItems.id)
        val songStarts = session[Sessions.nowPlayingSource] != "QUEUE" || session[Sessions.nowPlayingQueueItemId] != headId
        Sessions.update({ Sessions.id eq sessionId }) {
            it[nowPlayingSource] = "QUEUE"
            it[nowPlayingQueueItemId] = headId
            if (songStarts) it[nowPlayingHistorySuppressed] = session[Sessions.historyPaused]
            it[playbackState] = if (head != null) "PLAYING" else "IDLE"
            it[updatedAt] = Instant.now()
        }
    }

    /** Public, transaction-wrapped entry point for callers outside this repository (route handlers). */
    fun getNowPlaying(sessionId: String): NowPlayingDto? = transaction { resolveNowPlaying(sessionId) }

    internal fun resolveNowPlaying(sessionId: String): NowPlayingDto? {
        val session = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
        return when (session[Sessions.nowPlayingSource]) {
            "PLAY_NOW" ->
                session[Sessions.playNowVideoId]?.let { videoId ->
                    NowPlayingDto(
                        source = "PLAY_NOW",
                        queueItemId = null,
                        videoId = videoId,
                        title = formatVideoTitle(session[Sessions.playNowTitle].orEmpty()),
                        channelName = session[Sessions.playNowChannelName].orEmpty(),
                        thumbnailUrl = session[Sessions.playNowThumbnailUrl],
                        durationSeconds = session[Sessions.playNowDurationSeconds],
                        addedByDisplayName = null,
                    )
                }
            "QUEUE" ->
                session[Sessions.nowPlayingQueueItemId]?.let { queueItemId ->
                    QueueItems.selectAll().where { QueueItems.id eq queueItemId }.singleOrNull()?.let { item ->
                        NowPlayingDto(
                            source = "QUEUE",
                            queueItemId = item[QueueItems.id],
                            videoId = item[QueueItems.videoId],
                            title = formatVideoTitle(item[QueueItems.title]),
                            channelName = item[QueueItems.channelName],
                            thumbnailUrl = item[QueueItems.thumbnailUrl],
                            durationSeconds = item[QueueItems.durationSeconds],
                            addedByDisplayName = item[QueueItems.addedByDisplayName],
                        )
                    }
                }
            else -> null
        }
    }

    internal fun toSummary(session: ResultRow): SessionSummaryDto {
        val sessionId = session[Sessions.id]
        val participantCount = activeParticipantCount(sessionId)
        val queueLength =
            QueueItems
                .selectAll()
                .where { (QueueItems.sessionId eq sessionId) and (QueueItems.status eq "PENDING") }
                .count()
                .toInt()
        return SessionSummaryDto(
            id = sessionId,
            code = session[Sessions.code],
            joinUrl = "${AppConfig.fromEnv().publicBaseUrl}/join/${session[Sessions.code]}",
            playbackState = session[Sessions.playbackState],
            nowPlaying = resolveNowPlaying(sessionId),
            participantCount = participantCount,
            queueLength = queueLength,
            theme = session[Sessions.theme],
        )
    }

    private fun generateUniqueCode(): String {
        repeat(10) {
            val candidate = SessionCodeGenerator.generate()
            if (Sessions.selectAll().where { Sessions.code eq candidate }.empty()) return candidate
        }
        error("Could not generate a unique session code after 10 attempts")
    }
}
