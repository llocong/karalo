package com.karalo.feature.update.domain

import java.io.File
import java.time.LocalDate

private const val PERCENT = 100

/** What a release says it changed, grouped the way the What's new page shows it. */
data class ReleaseNotes(
    val new: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = new.isEmpty() && improved.isEmpty() && fixed.isEmpty()
}

/** A version of the TV app newer than the installed one, as karalo.app/download/latest.json describes it. */
data class UpdateRelease(
    val versionCode: Long,
    val version: String,
    val releaseDate: LocalDate?,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long?,
    val notes: ReleaseNotes,
)

/** Where the app is with its next version. Anything but [UpToDate] shows the dot on Settings. */
sealed interface UpdateState {
    data object UpToDate : UpdateState

    /** A newer version exists. [failed] is set after a download that didn't finish. */
    data class Available(
        val release: UpdateRelease,
        val failed: Boolean = false,
    ) : UpdateState

    data class Downloading(
        val release: UpdateRelease,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState {
        val percent: Int
            get() = if (totalBytes <= 0) 0 else (downloadedBytes * PERCENT / totalBytes).toInt().coerceIn(0, PERCENT)
    }

    /** Downloaded and checked; "Restart now" hands [apk] to the system installer. */
    data class ReadyToRestart(
        val release: UpdateRelease,
        val apk: File,
    ) : UpdateState

    val releaseOrNull: UpdateRelease?
        get() =
            when (this) {
                UpToDate -> null
                is Available -> release
                is Downloading -> release
                is ReadyToRestart -> release
            }
}

/** What happened when the user chose "Restart now". */
enum class InstallResult {
    /** The system installer is open. */
    STARTED,

    /** Android first needs "Install unknown apps" allowed for Karalo; its settings page is open. */
    NEEDS_PERMISSION,

    /** Nothing ready to install, or the installer couldn't be opened. */
    FAILED,
}

interface UpdateRepository {
    val state: kotlinx.coroutines.flow.StateFlow<UpdateState>

    /** The installed version's name, e.g. "0.3.0". */
    val installedVersion: String

    /** Looks for a newer version. Keeps the current state when offline or while downloading/ready. */
    suspend fun check()

    /** Downloads the available version in the background; no-op unless [UpdateState.Available]. */
    fun startDownload()

    /** Opens the system installer for a [UpdateState.ReadyToRestart] update. */
    fun install(): InstallResult
}
