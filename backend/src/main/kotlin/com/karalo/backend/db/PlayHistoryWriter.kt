package com.karalo.backend.db

import com.karalo.backend.db.tables.PlayHistory
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.insert
import java.time.Instant
import java.util.UUID

/**
 * Appends one finished song to play_history -- no guest reference, on purpose (see [PlayHistory]).
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
    }
}
