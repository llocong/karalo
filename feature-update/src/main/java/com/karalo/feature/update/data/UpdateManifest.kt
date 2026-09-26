package com.karalo.feature.update.data

import com.karalo.feature.update.domain.ReleaseNotes
import com.karalo.feature.update.domain.UpdateRelease
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * karalo.app/download/latest.json, written by .github/workflows/release.yml. Releases before the
 * in-app updater only have the first six fields; the rest are optional so those still parse.
 */
@Serializable
internal data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releasedAt: String? = null,
    val version: String? = null,
    val releaseDate: String? = null,
    val sizeBytes: Long? = null,
    val releaseNotes: ManifestNotes? = null,
) {
    fun toRelease(): UpdateRelease =
        UpdateRelease(
            versionCode = versionCode,
            version = version ?: versionName,
            releaseDate = parseDate(releaseDate) ?: parseInstantDate(releasedAt),
            apkUrl = apkUrl,
            sha256 = sha256.lowercase(),
            sizeBytes = sizeBytes,
            notes = releaseNotes?.let { ReleaseNotes(it.new, it.improved, it.fixed) } ?: ReleaseNotes(),
        )

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): UpdateManifest = json.decodeFromString(serializer(), text)
    }
}

@Serializable
internal data class ManifestNotes(
    val new: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
)

private fun parseDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun parseInstantDate(value: String?): LocalDate? =
    value?.let { runCatching { Instant.parse(it).atOffset(ZoneOffset.UTC).toLocalDate() }.getOrNull() }
