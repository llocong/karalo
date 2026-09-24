package com.karalo.backend.db

import com.karalo.backend.db.tables.PlayHistory
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A karaoke night ends after this long without a song finishing; the next one starts a new night. */
internal val KARAOKE_NIGHT_GAP: Duration = Duration.ofHours(2)

/**
 * Which night a play at [playedAt] belongs to, given the night and time of the play just before
 * it (null if there's none): that same night unless more than [KARAOKE_NIGHT_GAP] has passed.
 */
internal fun nightStartFor(
    playedAt: Instant,
    previousPlayedAt: Instant?,
    previousNightStartedAt: Instant?,
): Instant =
    if (previousPlayedAt != null &&
        previousNightStartedAt != null &&
        Duration.between(previousPlayedAt, playedAt) <= KARAOKE_NIGHT_GAP
    ) {
        previousNightStartedAt
    } else {
        playedAt
    }

/**
 * Appends one finished song to play_history -- no guest reference, on purpose (see [PlayHistory]).
 * Callers check the session's history switch first (see Sessions.nowPlayingHistorySuppressed).
 * Must be called inside a transaction.
 */
@Suppress("LongParameterList") // one parameter per history column
internal fun recordPlayed(
    sessionId: String,
    videoId: String,
    title: String,
    channelName: String,
    thumbnailUrl: String?,
    durationSeconds: Int?,
    source: String,
    skipped: Boolean,
    playedAt: Instant = Instant.now(),
) {
    val previous =
        PlayHistory
            .selectAll()
            .where { PlayHistory.sessionId eq sessionId }
            .orderBy(PlayHistory.playedAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
    val nightStartedAt = nightStartFor(playedAt, previous?.get(PlayHistory.playedAt), previous?.get(PlayHistory.nightStartedAt))
    PlayHistory.insert {
        it[id] = UUID.randomUUID().toString()
        it[PlayHistory.sessionId] = sessionId
        it[PlayHistory.videoId] = videoId
        // Clamped to the columns' sizes: Play-Now titles come from the TV's own (unbounded) fields.
        it[PlayHistory.title] = title.take((PlayHistory.title.columnType as VarCharColumnType).colLength)
        it[PlayHistory.channelName] = channelName.take((PlayHistory.channelName.columnType as VarCharColumnType).colLength)
        it[PlayHistory.thumbnailUrl] = thumbnailUrl
        it[PlayHistory.durationSeconds] = durationSeconds
        it[PlayHistory.playSource] = source
        it[PlayHistory.skipped] = skipped
        it[PlayHistory.playedAt] = playedAt
        it[PlayHistory.nightStartedAt] = nightStartedAt
    }
}
