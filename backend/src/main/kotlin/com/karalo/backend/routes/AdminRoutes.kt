package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.admin.AdminAuth
import com.karalo.backend.admin.LoginResult
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val ADMIN_COOKIE = "karalo_admin"

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
                if (!adminPage(call, auth)) return@post
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
                if (!adminPage(call, auth)) return@post
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

private fun resource(path: String): String = Application::class.java.getResource(path)?.readText().orEmpty()
