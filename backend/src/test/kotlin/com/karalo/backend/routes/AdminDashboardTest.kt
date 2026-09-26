package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.admin.AdminAuth
import com.karalo.backend.admin.AdminPassword
import com.karalo.backend.admin.LoginResult
import com.karalo.backend.config.AppConfig
import com.karalo.backend.module
import io.ktor.client.HttpClient
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PASSWORD = "correct horse battery"

// Few iterations, so tests don't spend a second on each sign-in.
private val PASSWORD_HASH = AdminPassword.hash(PASSWORD, iterations = 1_000)

/** The admin dashboard at /admin: sign-in, and the data behind Overview and Sessions. */
class AdminDashboardTest {
    private fun testConfig(adminPasswordHash: String? = PASSWORD_HASH) =
        AppConfig(
            dbPath = File.createTempFile("karalo-admin-test-", ".db").apply { deleteOnExit() }.absolutePath,
            adminPasswordHash = adminPasswordHash,
            trustProxy = true,
        )

    private fun ApplicationTestBuilder.startApp(config: AppConfig = testConfig()): () -> AppDependencies {
        lateinit var deps: AppDependencies
        application { deps = module(config) }
        return { deps }
    }

    @Test
    fun `without a password hash, the admin doesn't exist`() =
        testApplication {
            startApp(testConfig(adminPasswordHash = null))
            assertEquals(HttpStatusCode.NotFound, client.get("/admin").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/admin/api/overview").status)
            assertEquals(HttpStatusCode.NotFound, login(client, PASSWORD).status)
        }

    @Test
    fun `the page is public but never cached or indexed, and the data needs signing in`() =
        testApplication {
            startApp()
            val page = client.get("/admin")
            assertEquals(HttpStatusCode.OK, page.status)
            assertEquals("no-store", page.headers[HttpHeaders.CacheControl])
            assertEquals("noindex, nofollow", page.headers["X-Robots-Tag"])
            assertTrue(page.bodyAsText().contains("/admin/admin.js"))
            assertEquals(HttpStatusCode.OK, client.get("/admin/admin.js").status)

            for (path in listOf("/admin/api/overview", "/admin/api/sessions", "/admin/api/sessions/ABCD1234")) {
                assertEquals(HttpStatusCode.Unauthorized, client.get(path).status, path)
            }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/api/overview") { header(HttpHeaders.Cookie, "karalo_admin=made-up") }.status)
        }

    @Test
    fun `signing in sets a strict, http-only cookie for admin pages only, and signing out ends it`() =
        testApplication {
            startApp()
            assertEquals(HttpStatusCode.Unauthorized, login(client, "wrong password").status)

            val ok = login(client, PASSWORD)
            assertEquals(HttpStatusCode.OK, ok.status)
            val setCookie = ok.headers[HttpHeaders.SetCookie]!!
            assertTrue(setCookie.startsWith("karalo_admin="), setCookie)
            assertTrue(setCookie.contains("HttpOnly"), setCookie)
            assertTrue(setCookie.contains("SameSite=Strict"), setCookie)
            assertTrue(setCookie.contains("Path=/admin"), setCookie)
            val cookie = setCookie.substringBefore(';')

            assertEquals(HttpStatusCode.OK, client.get("/admin/api/overview") { header(HttpHeaders.Cookie, cookie) }.status)
            val me = json(client.get("/admin/api/me") { header(HttpHeaders.Cookie, cookie) })
            assertEquals("true", me["signedIn"]!!.jsonPrimitive.content)

            client.post("/admin/api/logout") { header(HttpHeaders.Cookie, cookie) }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/api/overview") { header(HttpHeaders.Cookie, cookie) }.status)
        }

    @Test
    fun `five wrong passwords lock that IP out, even with the right one, but not other IPs`() =
        testApplication {
            startApp()
            repeat(AdminAuth.MAX_FAILURES - 1) { assertEquals(HttpStatusCode.Unauthorized, login(client, "nope", "203.0.113.9").status) }
            val locked = login(client, "nope", "203.0.113.9")
            assertEquals(HttpStatusCode.TooManyRequests, locked.status)
            assertTrue(json(locked)["lockedForSeconds"]!!.jsonPrimitive.long > 0)
            assertEquals(HttpStatusCode.TooManyRequests, login(client, PASSWORD, "203.0.113.9").status)
            assertTrue(json(client.get("/admin/api/me") { header("X-Forwarded-For", "203.0.113.9") })["lockedForSeconds"]!!.jsonPrimitive.long > 0)

            assertEquals(HttpStatusCode.OK, login(client, PASSWORD, "203.0.113.10").status)
        }

    @Test
    fun `a lockout ends after 15 minutes, and sessions expire after 12 hours`() {
        var now = Instant.parse("2026-10-24T20:00:00Z")
        val auth = AdminAuth(PASSWORD_HASH, clock = { now })
        repeat(AdminAuth.MAX_FAILURES) { auth.login("ip", "nope") }
        assertIs<LoginResult.Locked>(auth.login("ip", PASSWORD))

        now = now.plus(AdminAuth.LOCKOUT).plusSeconds(1)
        val success = assertIs<LoginResult.Success>(auth.login("ip", PASSWORD))
        assertTrue(auth.isSignedIn(success.token))

        now = now.plus(AdminAuth.SESSION_LIFETIME).plus(Duration.ofSeconds(1))
        assertFalse(auth.isSignedIn(success.token))
    }

    @Test
    fun `a stored hash only matches its own password`() {
        assertTrue(AdminPassword.matches(PASSWORD, PASSWORD_HASH))
        assertFalse(AdminPassword.matches("correct horse battery!", PASSWORD_HASH))
        assertFalse(AdminPassword.matches(PASSWORD, "not a hash"))
    }

    @Test
    fun `overview counts today's usage, live parties and guests`() =
        testApplication {
            startApp()
            val cookie = signIn(client)
            val code = startParty(client, "tv-overview", appVersion = "0.3.0")
            repeat(3) { i -> joinParty(client, code, "Guest $i") }
            client.get("/download") { header(HttpHeaders.UserAgent, "okhttp/4.12.0") }

            val overview = json(client.get("/admin/api/overview?period=today") { header(HttpHeaders.Cookie, cookie) })
            val now = overview["now"]!!.jsonObject
            assertEquals(1, now["activeParties"]!!.jsonPrimitive.int)
            assertEquals(3, now["activeGuests"]!!.jsonPrimitive.int)
            val totals = overview["totals"]!!.jsonObject
            assertEquals(1, totals["parties"]!!.jsonPrimitive.int)
            assertEquals(3, totals["guests"]!!.jsonPrimitive.int)
            assertEquals(1, totals["newTvs"]!!.jsonPrimitive.int)
            assertEquals(1, totals["dlApp"]!!.jsonPrimitive.int)
            assertEquals("true", overview["hasData"]!!.jsonPrimitive.content)
            // Today is charted by hour: one bar per hour so far.
            assertTrue(overview["points"]!!.jsonArray.isNotEmpty())
            assertTrue(overview["points"]!!.jsonArray.first().jsonObject["key"]!!.jsonPrimitive.content.contains("T"))
            val versions = overview["appVersions"]!!.jsonArray.map { it.jsonObject["label"]!!.jsonPrimitive.content }
            assertEquals(listOf("0.3.0"), versions)

            val week = json(client.get("/admin/api/overview?period=7d") { header(HttpHeaders.Cookie, cookie) })
            assertEquals(7, week["points"]!!.jsonArray.size)
            // Statistics only started today, so there's nothing to compare to.
            assertEquals("null", week["previous"].toString())
        }

    @Test
    fun `sessions list each party, and a party's detail lists its guests`() =
        testApplication {
            startApp()
            val cookie = signIn(client)
            val code = startParty(client, "tv-sessions", appVersion = "0.3.0")
            joinParty(client, code, "Sophie")
            joinParty(client, code, "Marc-André")

            val sessions = Json.parseToJsonElement(client.get("/admin/api/sessions") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()).jsonArray
            val party = sessions.single().jsonObject
            assertEquals(code, party["code"]!!.jsonPrimitive.content)
            assertEquals("true", party["live"]!!.jsonPrimitive.content)
            assertEquals(2, party["activeGuests"]!!.jsonPrimitive.int)
            assertEquals("0.3.0", party["appVersion"]!!.jsonPrimitive.content)
            // No live connection in this test: the TV only talks over REST.
            assertEquals("reconnecting", party["tvStatus"]!!.jsonPrimitive.content)
            assertNotNull(party["startedAt"]!!.jsonPrimitive.content)

            val detail = json(client.get("/admin/api/sessions/${code.lowercase()}") { header(HttpHeaders.Cookie, cookie) })
            assertEquals(listOf("Sophie", "Marc-André"), detail["guests"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content })
            assertEquals(2, detail["joins"]!!.jsonArray.size)
            assertEquals(HttpStatusCode.NotFound, client.get("/admin/api/sessions/ZZZZZZZZ") { header(HttpHeaders.Cookie, cookie) }.status)
        }

    private suspend fun login(
        client: HttpClient,
        password: String,
        fromIp: String = "203.0.113.1",
    ): HttpResponse =
        client.post("/admin/api/login") {
            contentType(ContentType.Application.Json)
            header("X-Forwarded-For", fromIp)
            setBody("""{"password":"$password"}""")
        }

    private suspend fun signIn(client: HttpClient): String = login(client, PASSWORD).headers[HttpHeaders.SetCookie]!!.substringBefore(';')

    private suspend fun startParty(
        client: HttpClient,
        tvId: String,
        appVersion: String,
    ): String {
        val ensure = client.post("/api/tvs/$tvId/session/ensure") { header(TV_APP_VERSION_HEADER, appVersion) }
        return json(ensure)["session"]!!.jsonObject["code"]!!.jsonPrimitive.content
    }

    private suspend fun joinParty(
        client: HttpClient,
        code: String,
        name: String,
    ) {
        val response =
            client.post("/api/sessions/$code/participants") {
                contentType(ContentType.Application.Json)
                // Each guest from their own IP, so the join rate limit doesn't get in the way.
                header("X-Forwarded-For", "198.51.100.${name.hashCode() and 0xff}")
                setBody("""{"displayName":"$name"}""")
            }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    private suspend fun json(response: HttpResponse): JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
}
