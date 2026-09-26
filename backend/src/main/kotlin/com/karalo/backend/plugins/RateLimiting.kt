package com.karalo.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.header
import io.ktor.server.request.path
import kotlin.time.Duration.Companion.minutes

val JoinRateLimit = RateLimitName("join")
val SearchRateLimit = RateLimitName("search")
val QueueAddRateLimit = RateLimitName("queueAdd")
val SessionLookupRateLimit = RateLimitName("sessionLookup")
val CommandRateLimit = RateLimitName("command")
val RenameRateLimit = RateLimitName("rename")
val StatsRateLimit = RateLimitName("stats")

/**
 * Keyed by the presented bearer token where one exists (a participant/TV making authenticated
 * calls), falling back to remote IP for the pre-auth public routes (join, session lookup) — so two
 * phones on the same home Wi-Fi (same LAN-facing IP from the server's point of view, if behind one
 * router) don't share one participant's budget once they're each authenticated individually.
 */
private fun tokenOrIpKey(call: io.ktor.server.application.ApplicationCall): String =
    call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ") ?: call.request.origin.remoteHost

/**
 * MVP starting numbers, explicitly tunable — see the karaoke ADR. Search is the highest-priority
 * limit: every call re-scrapes live YouTube through this one shared backend IP (unlike the TV
 * app's own per-device requests today), so excessive volume risks YouTube anti-bot/IP-reputation
 * issues against this server's own address.
 */
fun Application.installRateLimiting() {
    install(RateLimit) {
        register(JoinRateLimit) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(SearchRateLimit) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
            requestKey { call -> tokenOrIpKey(call) }
        }
        register(QueueAddRateLimit) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> tokenOrIpKey(call) }
        }
        register(SessionLookupRateLimit) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(CommandRateLimit) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> tokenOrIpKey(call) }
        }
        register(RenameRateLimit) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> tokenOrIpKey(call) }
        }
        // Page-view beacons: a real visitor sends one per page load.
        register(StatsRateLimit) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        global {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
            // Only the API and live connections use this budget. Pages, scripts, fonts and images
            // don't: one page load is several of them, and every guest on a party's Wi-Fi shares
            // one public address, so counting them turned people away. The admin dashboard has its
            // own protection (sign-in lockout, data behind the session cookie).
            requestWeight { call, _ -> if (call.request.path().let { it.startsWith("/api/") || it.startsWith("/ws/") }) 1 else 0 }
        }
    }
}
