package com.karalo.backend.stats

import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/** Days are counted in Quebec time, where Karalo's parties happen, so a night isn't split at 8 p.m. */
val STATS_ZONE: ZoneId = ZoneId.of("America/Toronto")

/** What gets counted. Each is one row per day and dimension in daily_stats. */
object Metric {
    const val PAGEVIEW = "pageview" // dimension: page path
    const val PAGEVIEW_REFERRER = "pageview.referrer" // dimension: referring site, or "" for direct
    const val PAGEVIEW_LANG = "pageview.lang" // dimension: en | fr
    const val PAGEVIEW_DEVICE = "pageview.device" // dimension: phone | tablet | desktop
    const val VISITOR = "visitor" // unique visitors that day, dimension ""
    const val DOWNLOAD = "download" // karalo.app/download hits, dimension: browser | app
    const val TV_REGISTERED = "tv_registered" // new TV installations, dimension: app version
    const val SESSION_STARTED = "session_started" // a TV's session started or restarted
    const val GUEST_JOINED = "guest_joined"
    const val SONG_PLAYED = "song_played"
}

/**
 * Usage statistics as daily totals only: nothing here identifies a person or a device. Counts
 * are kept in memory and written by [flush] (every minute, and on shutdown), so a busy page
 * doesn't cost a SQLite write per hit on the single database connection. A crash loses at most
 * the last minute.
 *
 * Unique visitors are counted Plausible-style: a hash of the IP and user agent with a random salt
 * that is created for each day, kept only in memory and thrown away the next day, so a visitor
 * can't be recognized across days or looked up afterwards. A restart makes a new salt, so a
 * visitor that day may be counted twice.
 */
class StatsRecorder(
    private val clock: () -> Instant = Instant::now,
) {
    private data class Key(
        val day: LocalDate,
        val metric: String,
        val dimension: String,
    )

    private val pending = ConcurrentHashMap<Key, LongAdder>()

    private val random = SecureRandom()
    private var visitorDay: LocalDate? = null
    private var visitorSalt = ByteArray(0)
    private val visitorHashes = ConcurrentHashMap.newKeySet<String>()

    fun count(
        metric: String,
        dimension: String = "",
    ) {
        pending.computeIfAbsent(Key(today(), metric, dimension.take(MAX_DIMENSION_LENGTH))) { LongAdder() }.increment()
    }

    /** Counts [Metric.VISITOR] once per IP and user agent per day. */
    fun visitor(
        ip: String,
        userAgent: String,
    ) {
        val day = today()
        val hash =
            synchronized(this) {
                if (day != visitorDay) {
                    visitorDay = day
                    visitorSalt = ByteArray(SALT_BYTES).also(random::nextBytes)
                    visitorHashes.clear()
                }
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(visitorSalt)
                digest.update("$ip\n$userAgent".toByteArray())
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        if (visitorHashes.add(hash)) count(Metric.VISITOR)
    }

    /** Adds the counts gathered since the last flush to daily_stats. */
    fun flush() {
        if (pending.isEmpty()) return
        val batch = mutableMapOf<Key, Long>()
        for (key in pending.keys.toList()) {
            val value = pending.remove(key)?.sum() ?: continue
            if (value > 0) batch[key] = value
        }
        if (batch.isEmpty()) return
        transaction {
            for ((key, value) in batch) {
                exec(
                    """
                    INSERT INTO daily_stats (day, metric, dimension, value) VALUES (?, ?, ?, ?)
                    ON CONFLICT (day, metric, dimension) DO UPDATE SET value = value + excluded.value
                    """.trimIndent(),
                    listOf(
                        TextColumnType() to key.day.toString(),
                        TextColumnType() to key.metric,
                        TextColumnType() to key.dimension,
                        LongColumnType() to value,
                    ),
                )
            }
        }
    }

    /** Counts not yet flushed, for tests. */
    internal fun pendingCount(metric: String): Long = pending.filterKeys { it.metric == metric }.values.sumOf { it.sum() }

    internal fun pendingDays(): Set<LocalDate> = pending.keys.mapTo(mutableSetOf()) { it.day }

    private fun today(): LocalDate = clock().atZone(STATS_ZONE).toLocalDate()

    private companion object {
        const val SALT_BYTES = 32
        const val MAX_DIMENSION_LENGTH = 100
    }
}
