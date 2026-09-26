package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.plugins.StatsRateLimit
import com.karalo.backend.stats.Metric
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/** Pages that report views; anything else is counted as "other" so the table can't be filled with junk paths. */
private val COUNTED_PAGES = setOf("/", "/privacy.html")

/** Karalo's own hosts: a visit coming from one of these isn't a referral. */
private val OWN_HOSTS = setOf("karalo.app", "www.karalo.app", "localhost")

private val BOT_USER_AGENT = Regex("bot|crawl|spider|slurp|preview|headless|lighthouse|facebookexternalhit|curl|wget|python|java/", RegexOption.IGNORE_CASE)

private val beaconJson = Json { ignoreUnknownKeys = true }

/**
 * Usage statistics (see stats/StatsRecorder):
 * - `POST /api/stats/view`: the landing and privacy pages report a view with `navigator.sendBeacon`.
 * - `GET /download`: what visitors type into Downloader; counted, then sent on to the current APK,
 *   which Caddy serves from disk (deploy/Caddyfile).
 */
fun Route.statsRoutes(deps: AppDependencies) {
    rateLimit(StatsRateLimit) {
        post("/api/stats/view") {
            val userAgent = call.request.header(HttpHeaders.UserAgent).orEmpty()
            // sendBeacon posts a string as text/plain, so the body is parsed by hand.
            val body = runCatching { beaconJson.parseToJsonElement(call.receiveText()) as JsonObject }.getOrNull()
            if (body != null && isBrowser(userAgent)) {
                val path = body["path"]?.jsonPrimitive?.content.orEmpty()
                val lang = body["lang"]?.jsonPrimitive?.content.orEmpty()
                deps.stats.count(Metric.PAGEVIEW, if (path in COUNTED_PAGES) path else "other")
                deps.stats.count(Metric.PAGEVIEW_REFERRER, referrerHost(body["ref"]?.jsonPrimitive?.content))
                deps.stats.count(Metric.PAGEVIEW_LANG, if (lang == "fr") "fr" else "en")
                deps.stats.count(Metric.PAGEVIEW_DEVICE, deviceClass(userAgent))
                deps.stats.visitor(call.request.origin.remoteHost, userAgent)
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    for (path in listOf("/download", "/download/")) {
        get(path) {
            val userAgent = call.request.header(HttpHeaders.UserAgent).orEmpty()
            if (!BOT_USER_AGENT.containsMatchIn(userAgent)) {
                // Downloader and other apps don't send a browser user agent.
                deps.stats.count(Metric.DOWNLOAD, if (userAgent.startsWith("Mozilla/")) "browser" else "app")
            }
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondRedirect("/download/karalo.apk", permanent = false)
        }
    }
}

/** Every browser's user agent starts with "Mozilla/"; scripts and crawlers that don't, or that say so, aren't visitors. */
private fun isBrowser(userAgent: String): Boolean = userAgent.startsWith("Mozilla/") && !BOT_USER_AGENT.containsMatchIn(userAgent)

/** The referring site's host, or "" for a direct visit or a link from Karalo itself. */
internal fun referrerHost(ref: String?): String {
    if (ref.isNullOrBlank()) return ""
    val host = runCatching { URI(ref).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return ""
    return if (host in OWN_HOSTS || "www.$host" in OWN_HOSTS) "" else host
}

internal fun deviceClass(userAgent: String): String =
    when {
        Regex("iPad|Tablet", RegexOption.IGNORE_CASE).containsMatchIn(userAgent) -> "tablet"
        // Android phones say "Mobile"; Android tablets don't.
        userAgent.contains("Mobi") -> "phone"
        userAgent.contains("Android") -> "tablet"
        else -> "desktop"
    }
