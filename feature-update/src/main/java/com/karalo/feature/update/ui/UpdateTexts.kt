@file:Suppress("MagicNumber") // sizes and colors are the design's own values

package com.karalo.feature.update.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// The What's new notes' body text: the design's #E4DDEC, a touch softer than the page's white.
internal val NotesText = Color(0xFFE4DDEC)

private const val BYTES_PER_MB = 1_000_000.0
private val RELEASE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

/** "4.2" for 4.2 MB: one decimal, since the whole APK is only a few MB. */
internal fun megabytes(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / BYTES_PER_MB)

/** "Released September 22, 2026", or null when the release doesn't say. */
internal fun releasedLabel(date: LocalDate?): String? = date?.let { "Released ${RELEASE_DATE_FORMAT.format(it)}" }
