package com.karalo.backend.routes

import com.karalo.backend.config.AppConfig
import com.karalo.backend.module
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The settings for running the backend on the internet: the TV registration key and proxy-aware rate limits. */
class PublicHostingTest {
    private fun testConfig(
        tvRegistrationKey: String? = null,
        trustProxy: Boolean = false,
    ) = AppConfig(
        dbPath = File.createTempFile("karalo-hosting-test-", ".db").apply { deleteOnExit() }.absolutePath,
        tvRegistrationKey = tvRegistrationKey,
        trustProxy = trustProxy,
    )

    @Test
    fun `with a registration key, a new TV can only register by presenting it`() =
        testApplication {
            application { module(testConfig(tvRegistrationKey = "the-key")) }

            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/tvs/tv-a/session/ensure").status)
            val wrongKey = client.post("/api/tvs/tv-a/session/ensure") { header(TV_REGISTRATION_KEY_HEADER, "nope") }
            assertEquals(HttpStatusCode.Unauthorized, wrongKey.status)

            val registered = client.post("/api/tvs/tv-a/session/ensure") { header(TV_REGISTRATION_KEY_HEADER, "the-key") }
            assertEquals(HttpStatusCode.Created, registered.status)
            val secret = Json.parseToJsonElement(registered.bodyAsText()).jsonObject["tvSecret"]!!.jsonPrimitive.content

            // Once registered, the TV only needs its own secret.
            val again = client.post("/api/tvs/tv-a/session/ensure") { header("Authorization", "Bearer $secret") }
            assertEquals(HttpStatusCode.OK, again.status)
        }

    @Test
    fun `without a registration key, registration stays open`() =
        testApplication {
            application { module(testConfig()) }

            assertEquals(HttpStatusCode.Created, client.post("/api/tvs/tv-b/session/ensure").status)
        }

    @Test
    fun `behind a trusted proxy, each client IP gets its own join limit`() =
        testApplication {
            application { module(testConfig(trustProxy = true)) }
            val code = sessionCode(client)

            repeat(JOIN_LIMIT) { assertEquals(HttpStatusCode.Created, join(client, code, fromIp = "203.0.113.1").status) }
            assertEquals(HttpStatusCode.TooManyRequests, join(client, code, fromIp = "203.0.113.1").status)
            assertEquals(HttpStatusCode.Created, join(client, code, fromIp = "203.0.113.2").status)
        }

    @Test
    fun `without a trusted proxy, X-Forwarded-For is ignored`() =
        testApplication {
            application { module(testConfig()) }
            val code = sessionCode(client)

            repeat(JOIN_LIMIT) { assertEquals(HttpStatusCode.Created, join(client, code, fromIp = "203.0.113.1").status) }
            assertEquals(HttpStatusCode.TooManyRequests, join(client, code, fromIp = "203.0.113.2").status)
        }

    @Test
    fun `pages and their files don't count toward the rate limit, but the API does`() =
        testApplication {
            application { module(testConfig(trustProxy = true)) }

            // More than a minute's global budget of page loads from one address.
            repeat(GLOBAL_LIMIT + 10) {
                assertEquals(HttpStatusCode.OK, client.get("/") { header("X-Forwarded-For", "203.0.113.20") }.status)
                assertEquals(HttpStatusCode.OK, client.get("/fonts/manrope-latin.woff2") { header("X-Forwarded-For", "203.0.113.20") }.status)
            }
            val api = (1..GLOBAL_LIMIT + 1).map { client.get("/api/sessions/NOPE1234/queue") { header("X-Forwarded-For", "203.0.113.20") }.status }
            assertEquals(HttpStatusCode.TooManyRequests, api.last())
        }

    private suspend fun sessionCode(client: HttpClient): String {
        val ensure = client.post("/api/tvs/tv-proxy/session/ensure")
        return Json.parseToJsonElement(ensure.bodyAsText()).jsonObject["session"]!!.jsonObject["code"]!!.jsonPrimitive.content
    }

    private suspend fun join(
        client: HttpClient,
        code: String,
        fromIp: String,
    ) = client.post("/api/sessions/$code/participants") {
        header("X-Forwarded-For", fromIp)
        contentType(ContentType.Application.Json)
        setBody("""{"displayName":"Guest"}""")
    }

    private companion object {
        const val JOIN_LIMIT = 5
        const val GLOBAL_LIMIT = 60
    }
}
