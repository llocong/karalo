package com.karalo.backend.security

import com.karalo.backend.db.tables.SecurityEvents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

/**
 * Kinds of suspicious activity. Hits of one kind from the same source (and session, and rate
 * limit) are merged into one event while they keep coming within [mergeWindow]; an alert goes
 * out once an event reaches [alertAt] hits.
 */
enum class SecurityEventType(
    val key: String,
    val label: String,
    val mergeWindow: Duration,
    val alertAt: Long,
) {
    JOIN_BURST("burst", "Join burst", Duration.ofSeconds(30), 1),
    RATE_LIMITED("rate", "Rate limited", Duration.ofMinutes(5), 10),
    BAD_TV_KEY("key", "Wrong TV registration key", Duration.ofMinutes(5), 3),
    BAD_TV_SECRET("secret", "Wrong TV secret", Duration.ofMinutes(5), 3),
    INVALID_TOKEN("token", "Invalid guest token", Duration.ofMinutes(5), 5),
    CODE_GUESSING("guess", "Code guessing", Duration.ofMinutes(5), 1),
    SEARCH_SPIKE("spike", "Search spike", Duration.ofMinutes(5), 1),
    ;

    companion object {
        fun of(key: String): SecurityEventType? = entries.firstOrNull { it.key == key }
    }
}

/** Names of the rate limits (plugins/RateLimiting.kt), as the dashboard shows them. */
val RATE_LIMIT_LABELS =
    mapOf(
        "join" to "Join",
        "sessionLookup" to "Session lookup",
        "search" to "Search",
        "queueAdd" to "Queue add",
        "command" to "Command",
        "rename" to "Rename",
        "stats" to "Page views",
        "global" to "Global",
    )

/** When an alert should go out; see AlertSender. */
fun interface AlertSink {
    fun send(
        eventId: String,
        title: String,
        message: String,
    )
}

/** Thresholds, tunable in one place. */
data class SecurityThresholds(
    val joinBurst: Int = 10,
    val joinBurstWindow: Duration = Duration.ofSeconds(30),
    val codeGuesses: Int = 10,
    val codeGuessWindow: Duration = Duration.ofMinutes(10),
    val searchSpikeMin: Int = 60,
    val searchSpikeRatio: Double = 5.0,
    val alertThrottle: Duration = Duration.ofMinutes(15),
    val retention: Duration = Duration.ofDays(30),
)

/**
 * Spots suspicious activity from what the routes report (joins, rejected requests, rate-limit
 * hits, searches), keeps the events in memory while they're ongoing and writes them to
 * security_events on [flush] (every minute, with the usage statistics). Alerts go out right away.
 *
 * Sources are an HMAC of the IP address with a key kept in the database: the same address gives
 * the same source across days, so a repeat offender stands out, but the address itself is never
 * stored and can't be read back from the hash.
 */
class SecurityMonitor(
    hmacKey: ByteArray,
    private val alerts: AlertSink?,
    /** The code of a session, given its id or code, for alert texts; null if unknown. */
    private val sessionCodeOf: (String) -> String? = { null },
    private val thresholds: SecurityThresholds = SecurityThresholds(),
    private val clock: () -> Instant = Instant::now,
) {
    private val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(hmacKey, "HmacSHA256")) }

    private class OpenEvent(
        val id: String,
        val type: SecurityEventType,
        val sessionRef: String?,
        val source: String?,
        val detail: String?,
        val firstAt: Instant,
        var lastAt: Instant,
        var count: Long,
        val hits: ArrayDeque<Instant>,
        var alert: String = "none",
        var alertedAt: Instant? = null,
        var dirty: Boolean = true,
    )

    private val open = LinkedHashMap<String, OpenEvent>()
    private val lastAlert = HashMap<SecurityEventType, Instant>()
    private val joinWindows = HashMap<String, ArrayDeque<Pair<Instant, String>>>()
    private val guessWindows = HashMap<String, ArrayDeque<Instant>>()
    private val searchesPerMinute = HashMap<Long, Int>()
    private var firstSearchAt: Instant? = null
    private var lastCleanup = Instant.EPOCH

    /** The anonymized source for an IP address: 16 hex characters of its HMAC. */
    fun sourceOf(ip: String): String = synchronized(mac) { mac.doFinal(ip.toByteArray()) }.take(8).joinToString("") { "%02x".format(it) }

    @Synchronized
    fun onJoin(
        sessionId: String,
        ip: String,
    ) {
        val now = clock()
        val window = joinWindows.getOrPut(sessionId) { ArrayDeque() }
        window.addLast(now to sourceOf(ip))
        while (window.first().first.isBefore(now.minus(thresholds.joinBurstWindow))) window.removeFirst()
        val ongoing = find(SecurityEventType.JOIN_BURST, sessionId, null, null, now)
        when {
            ongoing != null -> hit(ongoing, now)
            window.size >= thresholds.joinBurst -> {
                val sources = window.map { it.second }.toSet()
                record(SecurityEventType.JOIN_BURST, sessionId, sources.singleOrNull(), null, window.map { it.first }, now)
            }
        }
        if (joinWindows.size > MAX_TRACKED) joinWindows.keys.first().let(joinWindows::remove)
    }

    /** A request named a session code that doesn't exist. Many from one source is someone guessing. */
    @Synchronized
    fun onUnknownSessionCode(ip: String) {
        val now = clock()
        val source = sourceOf(ip)
        val window = guessWindows.getOrPut(source) { ArrayDeque() }
        window.addLast(now)
        while (window.first().isBefore(now.minus(thresholds.codeGuessWindow))) window.removeFirst()
        val ongoing = find(SecurityEventType.CODE_GUESSING, null, source, null, now)
        when {
            ongoing != null -> hit(ongoing, now)
            window.size >= thresholds.codeGuesses -> record(SecurityEventType.CODE_GUESSING, null, source, null, window.toList(), now)
        }
        if (guessWindows.size > MAX_TRACKED) guessWindows.keys.first().let(guessWindows::remove)
    }

    /** One phone search (each is a live YouTube scrape). A sudden surge risks YouTube blocking the server. */
    @Synchronized
    fun onSearch() {
        val now = clock()
        val minute = now.epochSecond / 60
        searchesPerMinute.merge(minute, 1, Int::plus)
        searchesPerMinute.keys.removeAll { it <= minute - 60 }
        val ongoing = find(SecurityEventType.SEARCH_SPIKE, null, null, null, now)
        if (ongoing != null) return hit(ongoing, now)
        val recent = (0..4).sumOf { searchesPerMinute[minute - it] ?: 0 }
        val usual = (5..59).sumOf { searchesPerMinute[minute - it] ?: 0 } / 11.0 // per 5 minutes
        // Until there's half an hour of history (after a restart), "usual" isn't known yet: only a
        // much bigger surge counts, so a busy night right after a deploy isn't mistaken for one.
        val since = firstSearchAt ?: now.also { firstSearchAt = it }
        val spike =
            if (since.isAfter(now.minus(SEARCH_HISTORY_NEEDED))) {
                recent >= thresholds.searchSpikeMin * thresholds.searchSpikeRatio
            } else {
                recent >= thresholds.searchSpikeMin && recent >= thresholds.searchSpikeRatio * maxOf(usual, 1.0)
            }
        if (spike) {
            val ratio = if (usual >= 1.0) (recent / usual).roundToInt().toString() else null
            record(SecurityEventType.SEARCH_SPIKE, null, null, ratio, List(recent) { now }, now)
        }
    }

    /** A request was turned away by the rate limit [limiter] (a RATE_LIMIT_LABELS key). */
    @Synchronized
    fun onRateLimited(
        limiter: String,
        ip: String,
        sessionRef: String?,
    ) = event(SecurityEventType.RATE_LIMITED, sessionRef, sourceOf(ip), limiter)

    /** A TV was refused: a wrong registration key (a new TV) or a wrong secret (a known one, [tvId]). */
    @Synchronized
    fun onRejectedTv(
        wrongKey: Boolean,
        ip: String,
        tvId: String?,
    ) = if (wrongKey) {
        event(SecurityEventType.BAD_TV_KEY, null, sourceOf(ip), null)
    } else {
        event(SecurityEventType.BAD_TV_SECRET, null, sourceOf(ip), tvId?.take(MAX_DETAIL))
    }

    /** A phone presented a guest token the session doesn't know. */
    @Synchronized
    fun onInvalidGuestToken(
        ip: String,
        sessionRef: String?,
    ) = event(SecurityEventType.INVALID_TOKEN, sessionRef, sourceOf(ip), null)

    private fun event(
        type: SecurityEventType,
        sessionRef: String?,
        source: String?,
        detail: String?,
    ) {
        val now = clock()
        val ongoing = find(type, sessionRef, source, detail, now)
        if (ongoing != null) hit(ongoing, now) else record(type, sessionRef, source, detail, listOf(now), now)
    }

    private fun key(
        type: SecurityEventType,
        sessionRef: String?,
        source: String?,
        detail: String?,
    ) = listOf(type.key, sessionRef.orEmpty(), source.orEmpty(), detail.orEmpty()).joinToString("|")

    private fun find(
        type: SecurityEventType,
        sessionRef: String?,
        source: String?,
        detail: String?,
        now: Instant,
    ): OpenEvent? = open[key(type, sessionRef, source, detail)]?.takeIf { !it.lastAt.isBefore(now.minus(type.mergeWindow)) }

    private fun record(
        type: SecurityEventType,
        sessionRef: String?,
        source: String?,
        detail: String?,
        hits: List<Instant>,
        now: Instant,
    ) {
        val event =
            OpenEvent(
                id = UUID.randomUUID().toString(),
                type = type,
                sessionRef = sessionRef?.take(MAX_DETAIL),
                source = source,
                detail = detail,
                firstAt = hits.minOrNull() ?: now,
                lastAt = now,
                count = hits.size.toLong(),
                hits = ArrayDeque(hits.takeLast(MAX_HITS)),
            )
        open.remove(key(type, event.sessionRef, source, detail))?.let { if (it.dirty) finished.add(it) }
        open[key(type, event.sessionRef, source, detail)] = event
        if (open.size > MAX_TRACKED) open.keys.first().let { k -> open.remove(k)?.let { if (it.dirty) finished.add(it) } }
        maybeAlert(event, now)
    }

    private fun hit(
        event: OpenEvent,
        now: Instant,
    ) {
        event.count++
        event.lastAt = now
        event.hits.addLast(now)
        if (event.hits.size > MAX_HITS) event.hits.removeFirst()
        event.dirty = true
        maybeAlert(event, now)
    }

    /** Events replaced in [open] before their last change was written. */
    private val finished = mutableListOf<OpenEvent>()

    private fun maybeAlert(
        event: OpenEvent,
        now: Instant,
    ) {
        if (event.alert != "none" || event.count < event.type.alertAt || alerts == null) return
        val last = lastAlert[event.type]
        if (last != null && last.isAfter(now.minus(thresholds.alertThrottle))) {
            event.alert = "throttled"
            return
        }
        event.alert = "sent"
        event.alertedAt = now
        lastAlert[event.type] = now
        val code = event.sessionRef?.let(sessionCodeOf)
        val text = describe(event.type, event.count, Duration.between(event.firstAt, event.lastAt), code, event.detail)
        runCatching { alerts?.send(event.id, "Karalo: ${event.type.label}", text + (code?.let { " · $it" } ?: "")) }
    }

    internal data class Snapshot(
        val type: SecurityEventType,
        val count: Long,
        val alert: String,
        val sessionRef: String?,
        val source: String?,
        val detail: String?,
    )

    /** The events still in memory, for tests. */
    @Synchronized
    internal fun openEvents(): List<Snapshot> = open.values.map { Snapshot(it.type, it.count, it.alert, it.sessionRef, it.source, it.detail) }

    /** Writes new and changed events, forgets finished ones, and deletes events past [SecurityThresholds.retention]. */
    fun flush() {
        val now = clock()
        val batch =
            synchronized(this) {
                val changed = finished.toMutableList().also { finished.clear() }
                changed += open.values.filter { it.dirty }
                changed.forEach { it.dirty = false }
                open.entries.removeAll { (_, e) -> e.lastAt.isBefore(now.minus(e.type.mergeWindow)) }
                changed.map { it to it.hits.toList() }
            }
        val cleanup = synchronized(this) { lastCleanup.isBefore(now.minus(Duration.ofHours(1))).also { if (it) lastCleanup = now } }
        if (batch.isEmpty() && !cleanup) return
        transaction {
            for ((event, hits) in batch) {
                val hitsJson = JsonArray(hits.map { JsonPrimitive(it.toEpochMilli()) }).toString()
                val code = event.sessionRef?.let(sessionCodeOf)
                val exists = SecurityEvents.selectAll().where { SecurityEvents.id eq event.id }.any()
                if (exists) {
                    SecurityEvents.update({ SecurityEvents.id eq event.id }) {
                        it[lastAt] = event.lastAt
                        it[count] = event.count
                        it[alert] = event.alert
                        it[alertedAt] = event.alertedAt
                        it[this.hits] = hitsJson
                        if (code != null) it[sessionCode] = code
                    }
                } else {
                    SecurityEvents.insert {
                        it[id] = event.id
                        it[type] = event.type.key
                        it[sessionRef] = event.sessionRef
                        it[sessionCode] = code
                        it[sourceHash] = event.source
                        it[detail] = event.detail
                        it[firstAt] = event.firstAt
                        it[lastAt] = event.lastAt
                        it[count] = event.count
                        it[alert] = event.alert
                        it[alertedAt] = event.alertedAt
                        it[this.hits] = hitsJson
                    }
                }
            }
            if (cleanup) SecurityEvents.deleteWhere { lastAt less now.minus(thresholds.retention) }
        }
    }

    companion object {
        private val SEARCH_HISTORY_NEEDED = Duration.ofMinutes(30)
        private const val MAX_HITS = 300
        private const val MAX_TRACKED = 10_000
        private const val MAX_DETAIL = 64

        /** The hit times stored in security_events.hits. */
        fun parseHits(json: String): List<Long> = runCatching { Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.long } }.getOrDefault(emptyList())

        /** One line saying what happened, for the dashboard and alerts. */
        fun describe(
            type: SecurityEventType,
            count: Long,
            span: Duration,
            sessionCode: String?,
            detail: String?,
        ): String =
            when (type) {
                SecurityEventType.JOIN_BURST -> "$count guests joined ${sessionCode ?: "a session"} in ${spanText(span)}"
                SecurityEventType.RATE_LIMITED -> "${RATE_LIMIT_LABELS[detail] ?: "Request"} limit hit ${times(count)}"
                SecurityEventType.BAD_TV_KEY -> "A TV tried to register without a valid key" + if (count > 1) " (${times(count)})" else ""
                SecurityEventType.BAD_TV_SECRET ->
                    "A TV connected${detail?.let { " as tv-" + it.removePrefix("tv-").take(4) + "…" }.orEmpty()} with the wrong secret"
                SecurityEventType.INVALID_TOKEN -> "A phone used an invalid guest token" + if (count > 1) " ${times(count)}" else ""
                SecurityEventType.CODE_GUESSING -> "$count unknown session codes looked up from one source"
                SecurityEventType.SEARCH_SPIKE -> if (detail != null) "Searches $detail× the usual rate" else "$count searches in 5 minutes"
            }

        private fun times(count: Long) = if (count == 1L) "once" else "$count times"

        private fun spanText(span: Duration): String {
            val seconds = maxOf(1, span.seconds)
            return if (seconds < 120) "$seconds s" else "${seconds / 60} min"
        }
    }
}
