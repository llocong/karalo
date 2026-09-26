package com.karalo.backend.db

import com.karalo.backend.admin.ActiveTvsDto
import com.karalo.backend.admin.AdminGuestDto
import com.karalo.backend.admin.AdminNowDto
import com.karalo.backend.admin.AdminNowPlayingDto
import com.karalo.backend.admin.AdminSessionDetailDto
import com.karalo.backend.admin.AdminSessionDto
import com.karalo.backend.admin.BreakdownRowDto
import com.karalo.backend.admin.OverviewDto
import com.karalo.backend.admin.UsageKey
import com.karalo.backend.admin.UsagePointDto
import com.karalo.backend.db.tables.DailyStats
import com.karalo.backend.db.tables.HourlyStats
import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.db.tables.TvInstallations
import com.karalo.backend.domain.SessionCodeGenerator
import com.karalo.backend.realtime.RoomConnections
import com.karalo.backend.stats.Metric
import com.karalo.backend.stats.STATS_ZONE
import com.karalo.backend.stats.hourKey
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Read-only queries behind the admin dashboard (see routes/AdminRoutes). */
class AdminRepository(
    private val sessionRepository: SessionRepository,
) {
    private data class StatRow(
        val day: LocalDate,
        val metric: String,
        val dimension: String,
        val value: Long,
    )

    @Suppress("LongMethod") // one block per part of the Overview screen
    fun overview(
        period: String,
        connections: Map<String, RoomConnections>,
        now: Instant = Instant.now(),
    ): OverviewDto =
        transaction {
            val today = now.atZone(STATS_ZONE).toLocalDate()
            val length = PERIOD_DAYS[period] ?: PERIOD_DAYS.getValue(DEFAULT_PERIOD)
            val start = today.minusDays(length - 1L)
            val previousStart = start.minusDays(length.toLong())

            val rows =
                DailyStats.selectAll().map {
                    StatRow(LocalDate.parse(it[DailyStats.day]), it[DailyStats.metric], it[DailyStats.dimension], it[DailyStats.value])
                }
            val statsStartedOn = rows.filter { it.metric != Metric.GITHUB_DOWNLOADS_TOTAL }.minOfOrNull { it.day }
            val byDay = rows.groupBy { it.day }
            val gauges = rows.filter { it.metric == Metric.GITHUB_DOWNLOADS_TOTAL }.associate { it.day to it.value }.toSortedMap()
            val tvRows = TvInstallations.selectAll().toList()
            val tvCreated = tvRows.map { it[TvInstallations.createdAt] }

            fun githubOn(day: LocalDate): Long {
                val upTo = gauges.headMap(day.plusDays(1))
                if (upTo.isEmpty()) return 0
                val before = gauges.headMap(day)
                return if (before.isEmpty()) 0 else upTo.getValue(upTo.lastKey()) - before.getValue(before.lastKey())
            }

            fun dayValues(day: LocalDate): Map<String, Long> {
                val stats = byDay[day].orEmpty()

                fun sum(
                    metric: String,
                    dimension: String? = null,
                ) = stats.filter { it.metric == metric && (dimension == null || it.dimension == dimension) }.sumOf { it.value }
                return mapOf(
                    UsageKey.VISITS to sum(Metric.PAGEVIEW),
                    UsageKey.VISITORS to sum(Metric.VISITOR),
                    UsageKey.DOWNLOADS to sum(Metric.DOWNLOAD),
                    UsageKey.DOWNLOADS_BROWSER to sum(Metric.DOWNLOAD, "browser"),
                    UsageKey.DOWNLOADS_APP to sum(Metric.DOWNLOAD, "app"),
                    UsageKey.GITHUB to githubOn(day),
                    UsageKey.NEW_TVS to tvCreated.count { it.atZone(STATS_ZONE).toLocalDate() == day }.toLong(),
                    UsageKey.PARTIES to sum(Metric.SESSION_STARTED),
                    UsageKey.GUESTS to sum(Metric.GUEST_JOINED),
                    UsageKey.SONGS to sum(Metric.SONG_PLAYED),
                )
            }

            fun total(days: List<LocalDate>): Map<String, Long> =
                days.map(::dayValues).fold(UsageKey.ALL.associateWith { 0L }) { acc, values -> acc.mapValues { (k, v) -> v + values.getValue(k) } }

            val range = (0 until length).map { start.plusDays(it.toLong()) }
            val points =
                if (period == "today") {
                    hourlyPoints(today, now, tvCreated)
                } else {
                    range.map { UsagePointDto(it.toString(), dayValues(it)) }
                }
            val previous =
                if (statsStartedOn != null && !statsStartedOn.isAfter(previousStart)) {
                    total((0 until length).map { previousStart.plusDays(it.toLong()) })
                } else {
                    null
                }
            val inRange = rows.filter { !it.day.isBefore(start) && it.metric != Metric.GITHUB_DOWNLOADS_TOTAL }

            fun breakdown(metric: String) =
                inRange
                    .filter { it.metric == metric }
                    .groupBy { it.dimension }
                    .map { (label, list) -> BreakdownRowDto(label, list.sumOf { it.value }) }
                    .sortedByDescending { it.value }
            val seen = { ago: Duration -> tvRows.count { !it[TvInstallations.lastSeenAt].isBefore(now.minus(ago)) } }
            val appVersions =
                tvRows
                    .filter { !it[TvInstallations.lastSeenAt].isBefore(now.minus(Duration.ofDays(30))) }
                    .groupBy { it[TvInstallations.appVersion].orEmpty() }
                    .map { (version, list) -> BreakdownRowDto(version, list.size.toLong()) }
                    .sortedWith(compareBy<BreakdownRowDto> { it.label.isEmpty() }.thenByDescending { it.value })

            OverviewDto(
                now = now(connections),
                period = if (period in PERIOD_DAYS) period else DEFAULT_PERIOD,
                startDay = start.toString(),
                endDay = today.toString(),
                hasData = inRange.isNotEmpty(),
                statsStartedOn = statsStartedOn?.toString(),
                points = points,
                totals = total(range),
                previous = previous,
                activeTvs = ActiveTvsDto(seen(Duration.ofHours(24)), seen(Duration.ofDays(7)), seen(Duration.ofDays(30))),
                pages = breakdown(Metric.PAGEVIEW),
                referrers = breakdown(Metric.PAGEVIEW_REFERRER),
                languages = breakdown(Metric.PAGEVIEW_LANG),
                devices = breakdown(Metric.PAGEVIEW_DEVICE),
                appVersions = appVersions,
            )
        }

    /** Today, hour by hour up to the current hour. Must be called inside a transaction. */
    private fun hourlyPoints(
        today: LocalDate,
        now: Instant,
        tvCreated: List<Instant>,
    ): List<UsagePointDto> {
        val prefix = today.toString() + "T"
        val rows =
            HourlyStats
                .selectAll()
                .where { HourlyStats.hour greaterEq prefix }
                .filter { it[HourlyStats.hour].startsWith(prefix) }
                .groupBy({ it[HourlyStats.hour] }, { it[HourlyStats.metric] to it[HourlyStats.value] })
        val currentHour = now.atZone(STATS_ZONE).hour
        return (0..currentHour).map { hour ->
            val key = prefix + "%02d".format(hour)
            val stats = rows[key].orEmpty()
            val get = { metric: String -> stats.filter { it.first == metric }.sumOf { it.second } }
            UsagePointDto(
                key,
                mapOf(
                    UsageKey.VISITS to get(Metric.PAGEVIEW),
                    UsageKey.VISITORS to get(Metric.VISITOR),
                    UsageKey.DOWNLOADS to get(Metric.DOWNLOAD),
                    UsageKey.NEW_TVS to tvCreated.count { hourKey(it) == key }.toLong(),
                    UsageKey.PARTIES to get(Metric.SESSION_STARTED),
                    UsageKey.GUESTS to get(Metric.GUEST_JOINED),
                    UsageKey.SONGS to get(Metric.SONG_PLAYED),
                ),
            )
        }
    }

    /** Must be called inside a transaction. */
    private fun now(connections: Map<String, RoomConnections>): AdminNowDto {
        val live = liveSessionIds()
        return AdminNowDto(
            activeParties = live.size,
            tvsConnected = live.count { connections[it]?.tvConnected == true },
            phonesConnected = live.sumOf { connections[it]?.phones ?: 0 },
            activeGuests = live.sumOf { activeParticipantCount(it) },
        )
    }

    /** Ends every session whose TV went quiet (as any other read would), then lists those still going. */
    private fun liveSessionIds(): List<String> {
        Sessions
            .selectAll()
            .where { Sessions.endedAt.isNull() }
            .map { it[Sessions.id] }
            .forEach { endSessionIfTvInactive(it) }
        return Sessions.selectAll().where { Sessions.endedAt.isNull() }.map { it[Sessions.id] }
    }

    fun sessions(connections: Map<String, RoomConnections>): List<AdminSessionDto> =
        transaction {
            liveSessionIds()
            Sessions
                .selectAll()
                .map { toDto(it, connections) }
                .sortedWith(compareByDescending<AdminSessionDto> { it.live }.thenByDescending { it.startedAt ?: "" })
        }

    fun session(
        rawCode: String,
        connections: Map<String, RoomConnections>,
    ): AdminSessionDetailDto? =
        transaction {
            val row =
                Sessions.selectAll().where { Sessions.code eq SessionCodeGenerator.normalize(rawCode) }.singleOrNull()
                    ?: return@transaction null
            val sessionId = row[Sessions.id]
            endSessionIfTvInactive(sessionId)
            val fresh = Sessions.selectAll().where { Sessions.id eq sessionId }.single()
            val guests = partyGuests(fresh)
            AdminSessionDetailDto(
                session = toDto(fresh, connections),
                guests =
                    guests.map { guest ->
                        AdminGuestDto(
                            name = guest[Participants.displayName],
                            joinedAt = guest[Participants.createdAt].toString(),
                            lastActiveAt = guest[Participants.lastActiveAt]?.toString(),
                            songsQueued =
                                QueueItems
                                    .selectAll()
                                    .where { (QueueItems.addedByParticipantId eq guest[Participants.id]) and (QueueItems.status eq "PENDING") }
                                    .count()
                                    .toInt(),
                            removedReason = guest[Participants.removedReason],
                        )
                    },
                joins = guests.map { it[Participants.createdAt].toString() },
            )
        }

    /** The guests of the session's current (or last) party, oldest first. Must be called inside a transaction. */
    private fun partyGuests(session: ResultRow): List<ResultRow> {
        val startedAt = session[Sessions.startedAt]
        return Participants
            .selectAll()
            .where { Participants.sessionId eq session[Sessions.id] }
            .filter { startedAt == null || !it[Participants.createdAt].isBefore(startedAt) }
            .sortedBy { it[Participants.createdAt] }
    }

    /** Must be called inside a transaction. */
    private fun toDto(
        session: ResultRow,
        connections: Map<String, RoomConnections>,
    ): AdminSessionDto {
        val sessionId = session[Sessions.id]
        val tv = TvInstallations.selectAll().where { TvInstallations.id eq session[Sessions.tvInstallationId] }.single()
        val live = session[Sessions.endedAt] == null
        val connection = connections[sessionId]
        val nowPlaying = if (live) sessionRepository.resolveNowPlaying(sessionId) else null
        return AdminSessionDto(
            code = session[Sessions.code],
            tvId = tv[TvInstallations.id],
            appVersion = tv[TvInstallations.appVersion],
            theme = session[Sessions.theme],
            live = live,
            startedAt = session[Sessions.startedAt]?.toString(),
            endedAt = session[Sessions.endedAt]?.toString(),
            activeGuests = if (live) activeParticipantCount(sessionId) else 0,
            totalGuests = partyGuests(session).size,
            queue = QueueItems.selectAll().where { (QueueItems.sessionId eq sessionId) and (QueueItems.status eq "PENDING") }.count().toInt(),
            nowPlaying = nowPlaying?.let { AdminNowPlayingDto(it.title, it.channelName, it.durationSeconds) },
            tvStatus =
                when {
                    connection?.tvConnected == true -> "connected"
                    live -> "reconnecting"
                    else -> "offline"
                },
            tvLastSeen = tv[TvInstallations.lastSeenAt].toString(),
            phones = if (live) connection?.phones ?: 0 else 0,
        )
    }

    private companion object {
        const val DEFAULT_PERIOD = "7d"
        val PERIOD_DAYS = mapOf("today" to 1, "7d" to 7, "30d" to 30)
    }
}
