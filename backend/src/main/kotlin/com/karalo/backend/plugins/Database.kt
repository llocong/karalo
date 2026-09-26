package com.karalo.backend.plugins

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.nightStartFor
import com.karalo.backend.db.tables.DailyStats
import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.PlayHistory
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.db.tables.TvInstallations
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * `maximumPoolSize = 1` is the whole atomic-position-assignment strategy, not an oversight: SQLite
 * is single-writer regardless of app-level tricks, so pinning the pool to exactly one connection
 * means at most one `transaction { }` block anywhere in the process can be mid-flight at any
 * instant — full serialization by construction, no SQLITE_BUSY races, no manual locking needed for
 * "increment the queue's position cursor, then insert at that value" to be race-free. See
 * db/QueueRepository.kt's own doc for the exact mechanism this enables. This has a throughput
 * ceiling (effectively single-digit sustained writes/sec) — fine for one karaoke party's pace, a
 * documented follow-up (Postgres + SELECT ... FOR UPDATE) if this backend is ever asked to serve
 * many concurrent installations at real scale; the schema is already multi-tenant so that's a
 * driver/DDL swap later, not a redesign.
 */
fun connectDatabase(config: AppConfig): Database {
    val jdbcUrl = "jdbc:sqlite:file:${config.dbPath}?journal_mode=WAL&busy_timeout=5000&foreign_keys=on"
    val hikariConfig =
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            maximumPoolSize = 1
            driverClassName = "org.sqlite.JDBC"
        }
    val database = Database.connect(HikariDataSource(hikariConfig))
    transaction(database) {
        SchemaUtils.createMissingTablesAndColumns(TvInstallations, Sessions, Participants, QueueItems, PlayHistory, DailyStats)
        migrateToPlayHistory()
        backfillNightStartedAt()
    }
    return database
}

/**
 * One-off data migration for databases created before play_history and last_active_at existed --
 * idempotent, so it simply runs on every boot and does nothing once there's nothing left to move:
 * - gives every guest a last_active_at (their last_seen_at is the closest thing on record);
 * - copies played/skipped songs into play_history, then deletes every finished queue_items row
 *   (removed songs included), so queue_items only holds songs still in the queue.
 */
internal fun Transaction.migrateToPlayHistory() {
    exec("UPDATE participants SET last_active_at = last_seen_at WHERE last_active_at IS NULL")
    exec(
        """
        INSERT INTO play_history
            (id, session_id, video_id, title, channel_name, thumbnail_url, duration_seconds, source, skipped, played_at)
        SELECT id, session_id, video_id, title, channel_name, thumbnail_url, duration_seconds, 'QUEUE',
               status = 'SKIPPED', COALESCE(played_at, created_at)
        FROM queue_items
        WHERE status IN ('PLAYED', 'SKIPPED')
        """.trimIndent(),
    )
    exec("DELETE FROM queue_items WHERE status IN ('PLAYED', 'SKIPPED', 'REMOVED')")
}

/**
 * Gives history rows recorded before night_started_at existed their karaoke night, walking each
 * affected session's plays in order with the same rule new plays use (see nightStartFor).
 * Idempotent: only sessions that still have unassigned rows are touched.
 */
internal fun Transaction.backfillNightStartedAt() {
    val sessionIds =
        PlayHistory
            .select(PlayHistory.sessionId)
            .where { PlayHistory.nightStartedAt.isNull() }
            .withDistinct()
            .map { it[PlayHistory.sessionId] }
    for (sessionId in sessionIds) {
        var previousPlayedAt: Instant? = null
        var previousNight: Instant? = null
        val plays =
            PlayHistory
                .select(PlayHistory.id, PlayHistory.playedAt)
                .where { PlayHistory.sessionId eq sessionId }
                .orderBy(PlayHistory.playedAt to SortOrder.ASC, PlayHistory.id to SortOrder.ASC)
                .map { it[PlayHistory.id] to it[PlayHistory.playedAt] }
        for ((id, playedAt) in plays) {
            val night = nightStartFor(playedAt, previousPlayedAt, previousNight)
            PlayHistory.update({ PlayHistory.id eq id }) { it[nightStartedAt] = night }
            previousPlayedAt = playedAt
            previousNight = night
        }
    }
}
