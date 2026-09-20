package com.karalo.backend.plugins

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.db.tables.TvInstallations
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

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
        SchemaUtils.createMissingTablesAndColumns(TvInstallations, Sessions, Participants, QueueItems)
    }
    return database
}
