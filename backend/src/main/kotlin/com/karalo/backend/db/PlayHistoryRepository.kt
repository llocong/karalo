package com.karalo.backend.db

import com.karalo.backend.db.tables.PlayHistory
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.model.HistoryByDateDto
import com.karalo.backend.domain.model.HistoryPlayDto
import com.karalo.backend.domain.model.MostPlayedDto
import com.karalo.backend.domain.model.MostPlayedSongDto
import com.karalo.backend.youtube.formatVideoTitle
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.Base64

/** Largest page a client can ask for; the TV asks for pages of 50 as the list scrolls. */
internal const val HISTORY_MAX_PAGE_SIZE = 100

/**
 * Reads and manages a session's song history for the TV's History page. Every play stays its
 * own row; "most played" is an aggregation at read time, never a merge. Nothing here deletes
 * history on its own -- only an explicit [clear].
 */
class PlayHistoryRepository {
    /**
     * Newest plays first, keyset-paged on (played_at, id) so a page never shifts when new plays
     * land at the top between requests. [cursor] is the previous page's `nextCursor`.
     */
    fun byDate(
        sessionId: String,
        cursor: String?,
        limit: Int,
    ): HistoryByDateDto =
        transaction {
            val pageSize = limit.coerceIn(1, HISTORY_MAX_PAGE_SIZE)
            val after = cursor?.let(::decodeCursor)
            val rows =
                PlayHistory
                    .selectAll()
                    .where {
                        val inSession = PlayHistory.sessionId eq sessionId
                        if (after == null) {
                            inSession
                        } else {
                            val (playedAt, id) = after
                            inSession and
                                (
                                    (PlayHistory.playedAt less playedAt) or
                                        ((PlayHistory.playedAt eq playedAt) and (PlayHistory.id less id))
                                )
                        }
                    }.orderBy(PlayHistory.playedAt to SortOrder.DESC, PlayHistory.id to SortOrder.DESC)
                    .limit(pageSize + 1)
                    .toList()
            val page = rows.take(pageSize)
            HistoryByDateDto(
                items =
                    page.map {
                        HistoryPlayDto(
                            id = it[PlayHistory.id],
                            videoId = it[PlayHistory.videoId],
                            title = formatVideoTitle(it[PlayHistory.title]),
                            channelName = it[PlayHistory.channelName],
                            thumbnailUrl = it[PlayHistory.thumbnailUrl],
                            playedAt = it[PlayHistory.playedAt].toString(),
                            nightStartedAt = (it[PlayHistory.nightStartedAt] ?: it[PlayHistory.playedAt]).toString(),
                        )
                    },
                nextCursor = if (rows.size > pageSize) page.last().let { encodeCursor(it[PlayHistory.playedAt], it[PlayHistory.id]) } else null,
                paused = isPaused(sessionId),
            )
        }

    /**
     * One entry per video ID with its total play count, most played first (ties: most recently
     * played first). Title and picture come from the video's latest play.
     */
    fun mostPlayed(
        sessionId: String,
        offset: Int,
        limit: Int,
    ): MostPlayedDto =
        transaction {
            val pageSize = limit.coerceIn(1, HISTORY_MAX_PAGE_SIZE)
            val start = offset.coerceAtLeast(0)
            val playCount = PlayHistory.id.count()
            val lastPlayedAt = PlayHistory.playedAt.max()
            val groups =
                PlayHistory
                    .select(PlayHistory.videoId, playCount, lastPlayedAt)
                    .where { PlayHistory.sessionId eq sessionId }
                    .groupBy(PlayHistory.videoId)
                    .orderBy(playCount to SortOrder.DESC, lastPlayedAt to SortOrder.DESC, PlayHistory.videoId to SortOrder.ASC)
                    .limit(pageSize + 1)
                    .offset(start.toLong())
                    .toList()
            val page = groups.take(pageSize)
            val videoIds = page.map { it[PlayHistory.videoId] }
            val latestPlay =
                if (videoIds.isEmpty()) {
                    emptyMap()
                } else {
                    PlayHistory
                        .selectAll()
                        .where { (PlayHistory.sessionId eq sessionId) and (PlayHistory.videoId inList videoIds) }
                        .orderBy(PlayHistory.playedAt, SortOrder.DESC)
                        .toList()
                        .distinctBy { it[PlayHistory.videoId] }
                        .associateBy { it[PlayHistory.videoId] }
                }
            MostPlayedDto(
                items =
                    page.map { group ->
                        val videoId = group[PlayHistory.videoId]
                        val latest = latestPlay.getValue(videoId)
                        MostPlayedSongDto(
                            videoId = videoId,
                            title = formatVideoTitle(latest[PlayHistory.title]),
                            channelName = latest[PlayHistory.channelName],
                            thumbnailUrl = latest[PlayHistory.thumbnailUrl],
                            playCount = group[playCount].toInt(),
                            lastPlayedAt = (group[lastPlayedAt] ?: latest[PlayHistory.playedAt]).toString(),
                        )
                    },
                nextOffset = if (groups.size > pageSize) start + pageSize else null,
                paused = isPaused(sessionId),
            )
        }

    /**
     * Turns recording on or off. Pausing also leaves the song playing right now out (it wasn't
     * recorded for its whole run); resuming only affects songs that start afterwards.
     */
    fun setPaused(
        sessionId: String,
        paused: Boolean,
    ): Boolean =
        transaction {
            Sessions.update({ Sessions.id eq sessionId }) {
                it[historyPaused] = paused
                if (paused) it[nowPlayingHistorySuppressed] = true
            }
            paused
        }

    /** Permanently removes every play in this session's history. */
    fun clear(sessionId: String) {
        transaction { PlayHistory.deleteWhere { PlayHistory.sessionId eq sessionId } }
    }

    private fun isPaused(sessionId: String): Boolean =
        Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull()?.get(Sessions.historyPaused) ?: false

    private fun encodeCursor(
        playedAt: Instant,
        id: String,
    ): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$playedAt|$id".toByteArray())

    private fun decodeCursor(cursor: String): Pair<Instant, String> {
        val decoded = runCatching { String(Base64.getUrlDecoder().decode(cursor)) }.getOrNull()
        val parts = decoded?.split('|', limit = 2)
        val playedAt = parts?.getOrNull(0)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val id = parts?.getOrNull(1)
        if (playedAt == null || id == null) throw ApiException.Validation("Invalid history cursor")
        return playedAt to id
    }
}
