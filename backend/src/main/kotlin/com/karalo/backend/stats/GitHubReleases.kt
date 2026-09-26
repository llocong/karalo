package com.karalo.backend.stats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads how many times the TV app's APKs were downloaded from GitHub releases. GitHub only gives
 * a running total per file, so the dashboard compares the total recorded each day (see
 * [Metric.GITHUB_DOWNLOADS_TOTAL]) to get downloads per period. Unauthenticated calls are limited
 * to 60 an hour; this makes one.
 */
class GitHubReleases(
    private val client: OkHttpClient,
    /** "owner/name"; see AppConfig.githubRepo. */
    private val repo: String,
) {
    /** The total of every release's APK download counts, or null if GitHub couldn't be reached. */
    fun apkDownloadTotal(): Long? {
        val request =
            Request
                .Builder()
                .url("https://api.github.com/repos/$repo/releases?per_page=100")
                .header("Accept", "application/vnd.github+json")
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                apkDownloadTotal(Json.parseToJsonElement(response.body!!.string()).jsonArray)
            }
        }.getOrNull()
    }

    internal companion object {
        fun apkDownloadTotal(releases: JsonArray): Long =
            releases.sumOf { release ->
                release.jsonObject["assets"]?.jsonArray.orEmpty().sumOf { asset ->
                    val file = asset.jsonObject
                    if (file["name"]?.jsonPrimitive?.content.orEmpty().endsWith(".apk")) {
                        file["download_count"]?.jsonPrimitive?.long ?: 0L
                    } else {
                        0L
                    }
                }
            }
    }
}
