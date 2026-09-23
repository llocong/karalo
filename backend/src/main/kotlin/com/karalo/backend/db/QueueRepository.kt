package com.karalo.backend.db

import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.model.NowPlayingDto
import com.karalo.backend.domain.model.QueueItemDto
import com.karalo.backend.youtube.formatVideoTitle
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern

private val VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$")

/**
 * Owns the persistent, multi-phone-shared queue. The one requirement every method here exists to
 * satisfy precisely: queue positions are assigned server-side, atomically, and can never collide
 * even under concurrent adds from multiple phones — see [add]'s doc for the exact mechanism.
 */
class QueueRepository(
    private val sessionRepository: SessionRepository,
) {
    /**
     * Atomic position assignment: bumps `sessions.queue_position_cursor` and inserts at the new
     * value, both inside one `transaction { }`. Because the whole process shares a single pooled
     * JDBC connection (see plugins/Database.kt's `maximumPoolSize = 1`), no other request's
     * transaction can interleave between the increment and the insert — two phones adding
     * concurrently can never be assigned the same position, without any manual locking. This is
     * deliberately NOT `currentQueue.length + 1` computed from a read — that's exactly the
     * client-side race this requirement forbids.
     */
    fun add(
        sessionId: String,
        participantId: String,
        participantName: String,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        durationSeconds: Int?,
    ): QueueItemDto {
        validateVideoId(videoId)
        require(title.length <= 200) { "title too long" }
        require(channelName.length <= 200) { "channelName too long" }
        thumbnailUrl?.let {
            require(it.length <= 500 && isWellFormedHttpsUrl(it)) {
                "thumbnailUrl must be an https URL of at most 500 characters"
            }
        }
        durationSeconds?.let { require(it in 0..36000) { "durationSeconds out of range" } }

        return transaction {
            Sessions.update({ Sessions.id eq sessionId }) {
                it.update(Sessions.queuePositionCursor) { Sessions.queuePositionCursor + 1L }
            }
            val newPosition = Sessions.selectAll().where { Sessions.id eq sessionId }.single()[Sessions.queuePositionCursor]
            val itemId = UUID.randomUUID().toString()
            val now = Instant.now()
            QueueItems.insert {
                it[id] = itemId
                it[QueueItems.sessionId] = sessionId
                it[position] = newPosition
                it[QueueItems.videoId] = videoId
                it[QueueItems.title] = title
                it[QueueItems.channelName] = channelName
                it[QueueItems.thumbnailUrl] = thumbnailUrl
                it[QueueItems.durationSeconds] = durationSeconds
                it[addedByParticipantId] = participantId
                it[addedByDisplayName] = participantName
                it[status] = "PENDING"
                it[createdAt] = now
            }
            // If nothing is currently playing (fresh session, or the queue just drained to the
            // waiting screen) -- but NOT if a Play-Now is in progress, which must stay untouched
            // -- promote this freshly-arrived item straight to now-playing. This is what lets the
            // TV react to a single signal (NOW_PLAYING_CHANGED) for both "remote-first, nothing
            // has ever played" and "waiting screen, a phone just added a song" instead of having
            // to separately reason about "queue became non-empty".
            val session = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
            val nothingPlaying = session[Sessions.nowPlayingSource] != "PLAY_NOW" && session[Sessions.nowPlayingQueueItemId] == null
            if (nothingPlaying) sessionRepository.syncNowPlayingToQueueHead(sessionId)
            toDto(QueueItems.selectAll().where { QueueItems.id eq itemId }.single())
        }
    }

    fun listPending(sessionId: String): List<QueueItemDto> =
        transaction {
            QueueItems
                .selectAll()
                .where { (QueueItems.sessionId eq sessionId) and (QueueItems.status eq "PENDING") }
                .orderBy(QueueItems.position, SortOrder.ASC)
                .map { toDto(it) }
        }

    fun delete(
        sessionId: String,
        queueItemId: String,
    ) = transaction {
        val item =
            QueueItems.selectAll().where { (QueueItems.id eq queueItemId) and (QueueItems.sessionId eq sessionId) }.singleOrNull()
                ?: throw ApiException.NotFound("Queue item not found")
        if (item[QueueItems.status] != "PENDING") throw ApiException.Conflict("Queue item is not pending")
        QueueItems.update({ QueueItems.id eq queueItemId }) { it[status] = "REMOVED" }
        // If the deleted item happened to be the resolved "now playing" pointer (shouldn't
        // normally happen — a PLAYING item isn't PENDING — but re-sync defensively so a stale
        // pointer can never linger after a delete).
        sessionRepository.syncNowPlayingToQueueHead(sessionId)
    }

    /**
     * [orderedQueueItemIds] must be exactly the current PENDING id set *excluding* whichever item
     * is currently the resolved now-playing pointer — a 409 signals the client's view is stale and
     * it should refetch. Excluding it matches exactly what `GET .../queue` shows as "queue" (see
     * that route's own doc: the now-playing item stays PENDING but is filtered out of the list
     * there); comparing against the *full* PENDING set here would make every reorder attempt fail
     * with a false-positive 409 the instant something is playing, since the client's submitted id
     * list — built from what it can actually see and drag around — never includes that item
     * either. The now-playing item's own position is left untouched (it's already the head);
     * renumbering starts after it. Renumbers using the same shared cursor [add] draws from, so a
     * reorder can never collide with a concurrent add (both pull from one atomically-incrementing
     * counter).
     */
    fun reorder(
        sessionId: String,
        orderedQueueItemIds: List<String>,
    ): List<QueueItemDto> =
        transaction {
            val session = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
            val nowPlayingQueueItemId =
                session[Sessions.nowPlayingQueueItemId].takeIf { session[Sessions.nowPlayingSource] == "QUEUE" }
            val current =
                QueueItems
                    .selectAll()
                    .where { (QueueItems.sessionId eq sessionId) and (QueueItems.status eq "PENDING") }
                    .toList()
            val currentIds = current.map { it[QueueItems.id] }.filterNot { it == nowPlayingQueueItemId }.toSet()
            if (currentIds != orderedQueueItemIds.toSet() || currentIds.size != orderedQueueItemIds.size) {
                throw ApiException.Conflict("Reorder id set is stale — refetch the queue and retry")
            }
            val n = orderedQueueItemIds.size
            Sessions.update({ Sessions.id eq sessionId }) {
                it.update(Sessions.queuePositionCursor) { Sessions.queuePositionCursor + n.toLong() }
            }
            val baseCursor = Sessions.selectAll().where { Sessions.id eq sessionId }.single()[Sessions.queuePositionCursor] - n
            orderedQueueItemIds.forEachIndexed { index, itemId ->
                QueueItems.update({ QueueItems.id eq itemId }) { it[position] = baseCursor + index + 1 }
            }
            sessionRepository.syncNowPlayingToQueueHead(sessionId)
            listPending(sessionId)
        }

    /**
     * "The currently-playing QUEUE item finished/was skipped" — marks it PLAYED or SKIPPED and
     * advances the now-playing pointer to whatever's newly at the head. Only meaningful when
     * something is actually currently playing FROM THE QUEUE (not a Play-Now item, which the TV
     * signals the end of via [SessionRepository.playNowEnd] instead) — a no-op on the pointer
     * otherwise, safe to call idempotently.
     */
    fun consumeNext(
        sessionId: String,
        skipped: Boolean,
    ): NowPlayingDto? =
        transaction {
            val session = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
            val currentItemId = session[Sessions.nowPlayingQueueItemId]
            if (session[Sessions.nowPlayingSource] == "QUEUE" && currentItemId != null) {
                QueueItems.update({ QueueItems.id eq currentItemId }) {
                    it[status] = if (skipped) "SKIPPED" else "PLAYED"
                    it[playedAt] = Instant.now()
                }
            }
            sessionRepository.syncNowPlayingToQueueHead(sessionId)
            sessionRepository.resolveNowPlaying(sessionId)
        }

    private fun validateVideoId(videoId: String) {
        if (!VIDEO_ID_PATTERN.matcher(videoId).matches()) throw ApiException.Validation("Invalid videoId")
    }

    // A phone supplies this URL and every other phone renders it as an <img src>: a mere
    // "https://" prefix check let through values carrying quotes/angle brackets that could break
    // out of that attribute. java.net.URI rejects those (and whitespace) outright, so parsing it
    // is what guarantees a single, well-formed URL. The web app escapes it on render too.
    private fun isWellFormedHttpsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrEmpty()
    }

    private fun toDto(row: ResultRow) =
        QueueItemDto(
            id = row[QueueItems.id],
            position = row[QueueItems.position],
            videoId = row[QueueItems.videoId],
            title = formatVideoTitle(row[QueueItems.title]),
            channelName = row[QueueItems.channelName],
            thumbnailUrl = row[QueueItems.thumbnailUrl],
            durationSeconds = row[QueueItems.durationSeconds],
            addedByParticipantId = row[QueueItems.addedByParticipantId],
            addedByDisplayName = row[QueueItems.addedByDisplayName],
            addedAt = row[QueueItems.createdAt].toString(),
        )
}
