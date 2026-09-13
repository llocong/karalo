package com.karalo.core.common.text

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE

/** Formats a duration in seconds as "m:ss", or "h:mm:ss" once it reaches an hour. */
fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
