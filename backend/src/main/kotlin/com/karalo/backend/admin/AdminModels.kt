package com.karalo.backend.admin

import kotlinx.serialization.Serializable

/** Usage metrics the dashboard charts, as keys of [UsagePointDto.values] and [OverviewDto.totals]. */
object UsageKey {
    const val VISITS = "visits"
    const val VISITORS = "visitors"
    const val DOWNLOADS = "downloads"
    const val DOWNLOADS_BROWSER = "dlBrowser"
    const val DOWNLOADS_APP = "dlApp"
    const val GITHUB = "github"
    const val NEW_TVS = "newTvs"
    const val PARTIES = "parties"
    const val GUESTS = "guests"
    const val SONGS = "songs"
    val ALL = listOf(VISITS, VISITORS, DOWNLOADS, DOWNLOADS_BROWSER, DOWNLOADS_APP, GITHUB, NEW_TVS, PARTIES, GUESTS, SONGS)
}

@Serializable
data class AdminNowDto(
    val activeParties: Int,
    val tvsConnected: Int,
    val phonesConnected: Int,
    val activeGuests: Int,
)

/** One bar of the usage chart: a day (`yyyy-MM-dd`) or, for Today, an hour (`yyyy-MM-ddTHH`). */
@Serializable
data class UsagePointDto(
    val key: String,
    val values: Map<String, Long>,
)

@Serializable
data class ActiveTvsDto(
    val day: Int,
    val week: Int,
    val month: Int,
)

@Serializable
data class BreakdownRowDto(
    val label: String,
    val value: Long,
)

@Serializable
data class OverviewDto(
    val now: AdminNowDto,
    val period: String,
    val startDay: String,
    val endDay: String,
    val hasData: Boolean,
    /** First day with any statistics, or null if none were recorded yet. */
    val statsStartedOn: String?,
    val points: List<UsagePointDto>,
    val totals: Map<String, Long>,
    /** The same totals for the period before, or null when statistics don't cover all of it. */
    val previous: Map<String, Long>?,
    val activeTvs: ActiveTvsDto,
    val pages: List<BreakdownRowDto>,
    val referrers: List<BreakdownRowDto>,
    val languages: List<BreakdownRowDto>,
    val devices: List<BreakdownRowDto>,
    /** TVs seen in the last 30 days by app version; "" for builds that don't report it. */
    val appVersions: List<BreakdownRowDto>,
)

@Serializable
data class AdminNowPlayingDto(
    val title: String,
    val artist: String,
    val durationSeconds: Int?,
)

@Serializable
data class AdminSessionDto(
    val code: String,
    val tvId: String,
    val appVersion: String?,
    val theme: String,
    val live: Boolean,
    val startedAt: String?,
    val endedAt: String?,
    val activeGuests: Int,
    val totalGuests: Int,
    val queue: Int,
    val nowPlaying: AdminNowPlayingDto?,
    /** connected | reconnecting | offline */
    val tvStatus: String,
    val tvLastSeen: String,
    val phones: Int,
)

@Serializable
data class AdminGuestDto(
    val name: String,
    val joinedAt: String,
    val lastActiveAt: String?,
    val songsQueued: Int,
    /** Null while the guest is in the party; otherwise GUEST_EXPIRED or SESSION_ENDED. */
    val removedReason: String?,
)

@Serializable
data class AdminSessionDetailDto(
    val session: AdminSessionDto,
    val guests: List<AdminGuestDto>,
    /** When each guest of this party joined, oldest first. */
    val joins: List<String>,
)
