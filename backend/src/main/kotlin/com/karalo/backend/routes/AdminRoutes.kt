package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.admin.AdminAuth
import com.karalo.backend.admin.LoginResult
import com.karalo.backend.domain.GUEST_REMOVED
import com.karalo.backend.plugins.appJson
import com.karalo.backend.security.AlertSender
import com.karalo.backend.security.SecurityThresholds
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val ADMIN_COOKIE = "karalo_admin"
private const val ADMIN_HEADER = "X-Karalo-Admin"

/**
 * The owner's dashboard at /admin (see admin/AdminAuth for sign-in). Everything here is 404
 * unless KARALO_ADMIN_PASSWORD_HASH is set, and never cached or indexed. The page and its script
 * are public; the data behind /admin/api needs the sign-in cookie, which only /admin receives.
 */
fun Route.adminRoutes(deps: AppDependencies) {
    val page = resource("/admin/index.html")
    val script = resource("/admin/admin.js")
    val auth = deps.adminAuth

    route("/admin") {
        get {
            if (!adminPage(call, auth)) return@get
            call.respondText(page, ContentType.Text.Html)
        }
        get("/admin.js") {
            if (!adminPage(call, auth)) return@get
            call.respondText(script, ContentType.Text.JavaScript)
        }

        route("/api") {
            get("/me") {
                if (!adminPage(call, auth)) return@get
                call.respond(
                    buildJsonObject {
                        put("signedIn", auth.isSignedIn(call.request.cookies[ADMIN_COOKIE]))
                        put("lockedForSeconds", auth.lockedFor(call.request.origin.remoteHost)?.seconds ?: 0)
                    },
                )
            }
            post("/login") {
                if (!adminPage(call, auth) || !sameOrigin(call)) return@post
                val password =
                    runCatching { (Json.parseToJsonElement(call.receiveText()) as JsonObject)["password"]?.jsonPrimitive?.content }
                        .getOrNull()
                        .orEmpty()
                when (val result = auth.login(call.request.origin.remoteHost, password)) {
                    is LoginResult.Success -> {
                        // Written by hand: Ktor's cookie helper adds a non-standard "$x-enc" attribute.
                        // Behind Caddy the origin is https; plain http only happens in local development.
                        val secure = if (call.request.origin.scheme == "https") "; Secure" else ""
                        call.response.header(
                            HttpHeaders.SetCookie,
                            "$ADMIN_COOKIE=${result.token}; Max-Age=${AdminAuth.SESSION_LIFETIME.seconds}; Path=/admin; HttpOnly; SameSite=Strict$secure",
                        )
                        call.respond(buildJsonObject { put("signedIn", true) })
                    }
                    LoginResult.WrongPassword -> call.respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "wrong") })
                    is LoginResult.Locked ->
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            buildJsonObject {
                                put("error", "locked")
                                put("lockedForSeconds", result.retryAfter.seconds)
                            },
                        )
                }
            }
            post("/logout") {
                if (!adminPage(call, auth) || !sameOrigin(call)) return@post
                auth.logout(call.request.cookies[ADMIN_COOKIE])
                call.response.header(HttpHeaders.SetCookie, "$ADMIN_COOKIE=; Max-Age=0; Path=/admin; HttpOnly; SameSite=Strict")
                call.respond(HttpStatusCode.NoContent)
            }

            get("/overview") {
                if (!signedIn(call, auth)) return@get
                // Include the last minute's counts, which are otherwise still in memory.
                deps.flushStats()
                val period = call.request.queryParameters["period"] ?: "7d"
                call.respond(deps.adminRepository.overview(period, deps.broadcaster.connections()))
            }
            get("/sessions") {
                if (!signedIn(call, auth)) return@get
                call.respond(deps.adminRepository.sessions(deps.broadcaster.connections()))
            }
            get("/summary") {
                if (!signedIn(call, auth)) return@get
                deps.flushStats()
                call.respond(deps.adminRepository.summary())
            }
            get("/security") {
                if (!signedIn(call, auth)) return@get
                deps.flushStats()
                val destination = deps.config.alertNtfyUrl?.let(AlertSender::masked)
                val throttle = SecurityThresholds().alertThrottle.toMinutes()
                call.respond(deps.adminRepository.security(call.request.queryParameters["period"] ?: "7d", destination, throttle))
            }
            post("/sessions/{code}/end") {
                if (!signedIn(call, auth) || !sameOrigin(call)) return@post
                val sessionId = deps.sessionRepository.idOf(call.parameters["code"].orEmpty())
                if (sessionId == null) return@post call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                // Ending closes the guests' phones (see onSessionEnded); the TV gets an empty queue.
                deps.sessionRepository.endNow(sessionId)
                deps.broadcaster.broadcast(sessionId, "QUEUE_UPDATED", appJson.encodeToJsonElement(deps.queueRepository.listPending(sessionId)))
                deps.broadcaster.broadcast(sessionId, "NOW_PLAYING_CHANGED", nowPlayingPayload(null))
                call.respond(HttpStatusCode.NoContent)
            }
            post("/sessions/{code}/guests/{participantId}/remove") {
                if (!signedIn(call, auth) || !sameOrigin(call)) return@post
                val sessionId = deps.sessionRepository.idOf(call.parameters["code"].orEmpty())
                val participantId = call.parameters["participantId"].orEmpty()
                if (sessionId == null || !deps.participantRepository.removeByAdmin(sessionId, participantId)) {
                    return@post call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                }
                deps.broadcaster.closeParticipant(sessionId, participantId, GUEST_REMOVED)
                deps.broadcaster.broadcast(sessionId, "QUEUE_UPDATED", appJson.encodeToJsonElement(deps.queueRepository.listPending(sessionId)))
                deps.broadcaster.broadcast(
                    sessionId,
                    "PARTICIPANT_LEFT",
                    buildJsonObject {
                        put("participantId", participantId)
                        put("participantCount", deps.participantRepository.participantCount(sessionId))
                    },
                )
                call.respond(HttpStatusCode.NoContent)
            }
            get("/sessions/{code}") {
                if (!signedIn(call, auth)) return@get
                val detail = deps.adminRepository.session(call.parameters["code"].orEmpty(), deps.broadcaster.connections())
                if (detail == null) call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") }) else call.respond(detail)
            }
        }
    }
}

/** Sets the headers every admin response gets; answers 404 (and returns false) while the admin is off. */
private suspend fun adminPage(
    call: ApplicationCall,
    auth: AdminAuth,
): Boolean {
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.response.header("X-Robots-Tag", "noindex, nofollow")
    call.response.header("Referrer-Policy", "no-referrer")
    if (!auth.enabled) {
        call.respond(HttpStatusCode.NotFound)
        return false
    }
    return true
}

/** [adminPage], plus a 401 (and false) unless the request carries a signed-in cookie. */
private suspend fun signedIn(
    call: ApplicationCall,
    auth: AdminAuth,
): Boolean {
    if (!adminPage(call, auth)) return false
    if (auth.isSignedIn(call.request.cookies[ADMIN_COOKIE])) return true
    call.respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "signed_out") })
    return false
}

/**
 * Requests that change something must come from the dashboard itself: they carry the
 * X-Karalo-Admin header, which another site can't add without the browser asking first (and
 * being refused), and an Origin, when sent, that's this server's own. With the SameSite=Strict
 * cookie, that keeps other pages from acting through the owner's browser.
 */
private suspend fun sameOrigin(call: ApplicationCall): Boolean {
    val origin = call.request.headers[HttpHeaders.Origin]
    val own = "${call.request.origin.scheme}://${call.request.headers[HttpHeaders.Host]}"
    if (call.request.headers[ADMIN_HEADER] == "1" && (origin == null || origin == own)) return true
    call.respond(HttpStatusCode.Forbidden, buildJsonObject { put("error", "forbidden") })
    return false
}

private fun resource(path: String): String = Application::class.java.getResource(path)?.readText().orEmpty()
