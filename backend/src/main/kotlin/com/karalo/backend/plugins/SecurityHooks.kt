package com.karalo.backend.plugins

import com.karalo.backend.domain.ApiException
import com.karalo.backend.security.SecurityMonitor
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path

/**
 * Reports what the security monitor watches that only shows in responses: requests turned away
 * by a rate limit (429) and successful phone searches. Rejected TVs and guests and unknown
 * session codes are reported from StatusPages, where the reason is known ([reportRejection]).
 */
fun Application.installSecurityHooks(security: SecurityMonitor) {
    install(
        createApplicationPlugin("SecurityHooks") {
            on(ResponseSent) { call ->
                val path = call.request.path()
                if (!path.startsWith("/api/")) return@on
                when (call.response.status()) {
                    HttpStatusCode.TooManyRequests -> security.onRateLimited(limiterFor(call.request.httpMethod, path), call.request.origin.remoteHost, sessionRefIn(path))
                    HttpStatusCode.OK -> if (SEARCH_PATH.matches(path)) security.onSearch()
                    else -> Unit
                }
            }
        },
    )
}

/** Tells the security monitor about a request refused with [cause]. */
fun reportRejection(
    security: SecurityMonitor,
    call: ApplicationCall,
    cause: ApiException,
) {
    val path = call.request.path()
    val ip = call.request.origin.remoteHost
    when {
        cause is ApiException.Unauthorized && cause.code == "UNAUTHORIZED" -> {
            val message = cause.message.orEmpty()
            when {
                "registration key" in message -> security.onRejectedTv(wrongKey = true, ip, tvId = null)
                "TV secret" in message -> security.onRejectedTv(wrongKey = false, ip, tvId = TV_PATH.find(path)?.groupValues?.get(1))
                "participant token" in message -> security.onInvalidGuestToken(ip, sessionRefIn(path))
            }
        }
        cause is ApiException.NotFound && cause.message == "Unknown session code" -> security.onUnknownSessionCode(ip)
    }
}

private val SEARCH_PATH = Regex("^/api/sessions/[^/]+/search$")
private val TV_PATH = Regex("^/api/tvs/([^/]+)/")
private val SESSION_PATH = Regex("^/api/sessions/([^/]+)")

private fun sessionRefIn(path: String): String? = SESSION_PATH.find(path)?.groupValues?.get(1)

/**
 * Which rate limit (plugins/RateLimiting.kt) turned a request away, from its route. A request can
 * also hit the global limit on one of these routes; it's then counted under the route's own name.
 */
internal fun limiterFor(
    method: HttpMethod,
    path: String,
): String {
    val parts = path.removePrefix("/api/").split('/')
    return when {
        path == "/api/stats/view" -> "stats"
        parts.firstOrNull() != "sessions" -> "global"
        parts.size == 2 && method == HttpMethod.Get -> "sessionLookup"
        parts.size == 3 && parts[2] == "participants" && method == HttpMethod.Post -> "join"
        parts.size == 3 && parts[2] == "search" -> "search"
        parts.size == 3 && parts[2] == "queue" && method == HttpMethod.Post -> "queueAdd"
        parts.size == 4 && parts[2] == "commands" -> "command"
        parts.size == 3 && parts[2] == "me" && method == HttpMethod.Patch -> "rename"
        else -> "global"
    }
}
