package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.admin.AdminPassword
import com.karalo.backend.config.AppConfig
import com.karalo.backend.module
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ADMIN_PASSWORD = "correct horse battery"

/** Suspicious activity reaching the dashboard, and the dashboard's End party / Remove guest actions. */
class SecurityRoutesTest {
    private fun ApplicationTestBuilder.startApp(): () -> AppDependencies {
        lateinit var deps: AppDependencies
        val config =
            AppConfig(
                dbPath = File.createTempFile("karalo-security-test-", ".db").apply { deleteOnExit() }.absolutePath,
                adminPasswordHash = AdminPassword.hash(ADMIN_PASSWORD, iterations = 1_000),
                tvRegistrationKey = "the-key",
                trustProxy = true,
            )
        application { deps = module(config) }
        return { deps }
    }

    @Test
    fun `rejected requests and rate-limit hits show up as security events`() =
        testApplication {
            startApp()
            val cookie = signIn(client)
            val code = startParty(client, "tv-sec")
            // A 6th join from one address within a minute is turned away by the join limit.
            repeat(6) { joinParty(client, code, "Guest $it", fromIp = "203.0.113.50") }
            repeat(3) { client.post("/api/tvs/tv-stranger/session/ensure") { header("X-Forwarded-For", "203.0.113.60") } }
            client.get("/api/sessions/$code/me") {
                header(HttpHeaders.Authorization, "Bearer not-a-token")
                header("X-Forwarded-For", "203.0.113.70")
            }
            repeat(10) { client.get("/api/sessions/NOPE${it}XYZ") { header("X-Forwarded-For", "203.0.113.80") } }

            val events = security(client, cookie)
            val byType = events.associateBy { it["type"]!!.jsonPrimitive.content }
            assertEquals(setOf("rate", "key", "token", "guess"), byType.keys)
            assertEquals("Join", byType.getValue("rate")["limiter"]!!.jsonPrimitive.content)
            assertEquals(code, byType.getValue("rate")["sessionCode"]!!.jsonPrimitive.content)
            assertEquals("3", byType.getValue("key")["count"]!!.jsonPrimitive.content)
            assertEquals("10 unknown session codes looked up from one source", byType.getValue("guess")["details"]!!.jsonPrimitive.content)
            assertTrue(byType.getValue("key")["source"]!!.jsonPrimitive.content.startsWith("ip·"))

            val summary = json(client.get("/admin/api/summary") { header(HttpHeaders.Cookie, cookie) })
            assertEquals("4", summary["recentEvents"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a join burst flags the party and shows in its detail`() =
        testApplication {
            startApp()
            val cookie = signIn(client)
            val code = startParty(client, "tv-burst")
            repeat(10) { joinParty(client, code, "Guest $it", fromIp = "198.51.100.$it") }

            assertEquals("burst", security(client, cookie).single()["type"]!!.jsonPrimitive.content)
            val sessions = Json.parseToJsonElement(client.get("/admin/api/sessions") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()).jsonArray
            assertEquals("true", sessions.single().jsonObject["flagged"]!!.jsonPrimitive.content)
            val detail = json(client.get("/admin/api/sessions/$code") { header(HttpHeaders.Cookie, cookie) })
            assertEquals("10", detail["bursts"]!!.jsonArray.single().jsonObject["count"]!!.jsonPrimitive.content)
        }

    @Test
    fun `admin actions need the dashboard's header and the same origin`() =
        testApplication {
            startApp()
            val cookie = signIn(client)
            val code = startParty(client, "tv-csrf")
            val bare = client.post("/admin/api/sessions/$code/end") { header(HttpHeaders.Cookie, cookie) }
            assertEquals(HttpStatusCode.Forbidden, bare.status)
            val foreign =
                client.post("/admin/api/sessions/$code/end") {
                    admin(cookie)
                    header(HttpHeaders.Origin, "https://evil.example")
                }
            assertEquals(HttpStatusCode.Forbidden, foreign.status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/admin/api/sessions/$code/end") { header("X-Karalo-Admin", "1") }.status)
        }

    @Test
    fun `ending a party signs every guest out, and the TV's next call starts a fresh one`() =
        testApplication {
            startApp()
            val cookie = signIn(client)
            val (code, tvSecret) = startPartyWithSecret(client, "tv-end")
            val guest = joinParty(client, code, "Sophie", fromIp = "198.51.100.1")

            assertEquals(HttpStatusCode.NoContent, client.post("/admin/api/sessions/$code/end") { admin(cookie) }.status)
            val me = client.get("/api/sessions/${guest.first}/me") { header(HttpHeaders.Authorization, "Bearer ${guest.second}") }
            assertEquals(HttpStatusCode.Unauthorized, me.status)
            assertTrue(me.bodyAsText().contains("SESSION_ENDED"))

            client.post("/api/tvs/tv-end/session/ensure") { header(HttpHeaders.Authorization, "Bearer $tvSecret") }
            val detail = json(client.get("/admin/api/sessions/$code") { header(HttpHeaders.Cookie, cookie) })
            assertEquals("true", detail["session"]!!.jsonObject["live"]!!.jsonPrimitive.content)
            assertEquals(0, detail["guests"]!!.jsonArray.size)
        }

    @Test
    fun `removing a guest signs them out and takes their songs out of the queue`() =
        testApplication {
            startApp()
            val cookie = signIn(client)
            val code = startParty(client, "tv-remove")
            val (sessionId, token) = joinParty(client, code, "Léa", fromIp = "198.51.100.2")
            joinParty(client, code, "Julien", fromIp = "198.51.100.3")
            val added =
                client.post("/api/sessions/$sessionId/queue") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"videoId":"dQw4w9WgXcQ","title":"Song","channelName":"Channel","thumbnailUrl":null,"durationSeconds":200}""")
                }
            assertTrue(added.status.value in 200..299, added.bodyAsText())

            val lea = json(client.get("/admin/api/sessions/$code") { header(HttpHeaders.Cookie, cookie) })["guests"]!!.jsonArray
                .map { it.jsonObject }
                .single { it["name"]!!.jsonPrimitive.content == "Léa" }
            val id = lea["id"]!!.jsonPrimitive.content
            assertEquals(HttpStatusCode.NoContent, client.post("/admin/api/sessions/$code/guests/$id/remove") { admin(cookie) }.status)

            val me = client.get("/api/sessions/$sessionId/me") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.Unauthorized, me.status)
            assertTrue(me.bodyAsText().contains("GUEST_REMOVED"))
            val detail = json(client.get("/admin/api/sessions/$code") { header(HttpHeaders.Cookie, cookie) })
            assertEquals(0, detail["session"]!!.jsonObject["queue"]!!.jsonPrimitive.content.toInt())
            assertEquals(1, detail["session"]!!.jsonObject["activeGuests"]!!.jsonPrimitive.content.toInt())
            // Removing them again finds no one.
            assertEquals(HttpStatusCode.NotFound, client.post("/admin/api/sessions/$code/guests/$id/remove") { admin(cookie) }.status)
        }

    private fun HttpRequestBuilder.admin(cookie: String) {
        header(HttpHeaders.Cookie, cookie)
        header("X-Karalo-Admin", "1")
    }

    private suspend fun signIn(client: HttpClient): String =
        client
            .post("/admin/api/login") {
                contentType(ContentType.Application.Json)
                header("X-Karalo-Admin", "1")
                setBody("""{"password":"$ADMIN_PASSWORD"}""")
            }.headers[HttpHeaders.SetCookie]!!
            .substringBefore(';')

    private suspend fun security(
        client: HttpClient,
        cookie: String,
    ): List<JsonObject> = json(client.get("/admin/api/security?period=today") { header(HttpHeaders.Cookie, cookie) })["events"]!!.jsonArray.map { it.jsonObject }

    private suspend fun startParty(
        client: HttpClient,
        tvId: String,
    ): String = startPartyWithSecret(client, tvId).first

    private suspend fun startPartyWithSecret(
        client: HttpClient,
        tvId: String,
    ): Pair<String, String> {
        val body = json(client.post("/api/tvs/$tvId/session/ensure") { header(TV_REGISTRATION_KEY_HEADER, "the-key") })
        return body["session"]!!.jsonObject["code"]!!.jsonPrimitive.content to body["tvSecret"]!!.jsonPrimitive.content
    }

    /** Returns the session id and the guest's token (or empty strings if the join was refused). */
    private suspend fun joinParty(
        client: HttpClient,
        code: String,
        name: String,
        fromIp: String,
    ): Pair<String, String> {
        val response =
            client.post("/api/sessions/$code/participants") {
                contentType(ContentType.Application.Json)
                header("X-Forwarded-For", fromIp)
                setBody("""{"displayName":"$name"}""")
            }
        if (response.status != HttpStatusCode.Created) return "" to ""
        val body = json(response)
        return body["sessionId"]!!.jsonPrimitive.content to body["participantToken"]!!.jsonPrimitive.content
    }

    private suspend fun json(response: HttpResponse): JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
}
