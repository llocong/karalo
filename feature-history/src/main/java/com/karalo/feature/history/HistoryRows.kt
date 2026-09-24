package com.karalo.feature.history

import com.karalo.core.karaoke.domain.HistoryPlay
import com.karalo.core.karaoke.domain.MostPlayedSong
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NIGHT_THIS_YEAR = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val NIGHT_OTHER_YEAR = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)

// An explicit pattern rather than a localized style: newer CLDR data puts a narrow no-break space
// before AM/PM, which renders inconsistently across TV fonts.
private val TIME_OF_DAY = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** "Tuesday, September 23" -- the date a karaoke night started, with the year only if it's not this one. */
internal fun nightLabel(
    nightStartedAt: Instant,
    zone: ZoneId,
    today: LocalDate,
): String {
    val date = nightStartedAt.atZone(zone).toLocalDate()
    return (if (date.year == today.year) NIGHT_THIS_YEAR else NIGHT_OTHER_YEAR).format(date)
}

internal fun timeOfDay(
    instant: Instant,
    zone: ZoneId,
): String = TIME_OF_DAY.format(instant.atZone(zone))

/**
 * Plays newest first, with a heading at the start of each karaoke night. A night is labelled by
 * the date it started, so one running past midnight stays under that date; two nights on the same
 * date (an afternoon and an evening) also get their start time, to tell them apart.
 */
internal fun rowsByDate(
    plays: List<HistoryPlay>,
    zone: ZoneId,
    today: LocalDate,
): List<HistoryRow> {
    val nights = plays.map { it.nightStartedAt }.distinct()
    val labels = nights.associateWith { nightLabel(it, zone, today) }
    val labelCounts = labels.values.groupingBy { it }.eachCount()
    val rows = ArrayList<HistoryRow>(plays.size + nights.size)
    var currentNight: Instant? = null
    for (play in plays) {
        if (play.nightStartedAt != currentNight) {
            currentNight = play.nightStartedAt
            val label = labels.getValue(currentNight)
            val shown = if (labelCounts.getValue(label) > 1) "$label · from ${timeOfDay(currentNight, zone)}" else label
            rows += HistoryRow.Header(key = "night-${currentNight.toEpochMilli()}", label = shown)
        }
        rows +=
            HistoryRow.Song(
                key = "play-${play.id}",
                videoId = play.videoId,
                title = play.title,
                channelName = play.channelName,
                thumbnailUrl = play.thumbnailUrl,
                detail = timeOfDay(play.playedAt, zone),
            )
    }
    return rows
}

/** One row per video, most played first, with a heading ("12 plays") wherever the count changes. */
internal fun rowsByPlayCount(songs: List<MostPlayedSong>): List<HistoryRow> {
    val rows = ArrayList<HistoryRow>(songs.size * 2)
    var currentCount: Int? = null
    for (song in songs) {
        if (song.playCount != currentCount) {
            currentCount = song.playCount
            rows +=
                HistoryRow.Header(
                    key = "count-$currentCount",
                    label =
                        if (currentCount ==
                            1
                        ) {
                            "1 play"
                        } else {
                            "$currentCount plays"
                        },
                )
        }
        rows +=
            HistoryRow.Song(
                key = "video-${song.videoId}",
                videoId = song.videoId,
                title = song.title,
                channelName = song.channelName,
                thumbnailUrl = song.thumbnailUrl,
                detail = "",
            )
    }
    return rows
}
