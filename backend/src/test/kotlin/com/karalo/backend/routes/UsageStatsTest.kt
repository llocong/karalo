package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.tables.DailyStats
import com.karalo.backend.db.tables.TvInstallations
import com.karalo.backend.module
import com.karalo.backend.stats.Metric
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val PHONE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
private const val DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"

/** First-party usage statistics: page views, visitors, downloads and TV/party activity. */
class UsageStatsTest {
    private fun testConfig() =
        AppConfig(
            dbPath = File.createTempFile("karalo-stats-test-", ".db").apply { deleteOnExit() }.absolutePath,
            trustProxy = true,
        )

    private fun ApplicationTestBuilder.startApp(): () -> AppDependencies {
        lateinit var deps: AppDependencies
        application { deps = module(testConfig()) }
        return { deps }
    }

    @Test
    fun `page views are counted by page, referrer, language and device, and visitors once a day`() =
        testApplication {
            val deps = startApp()
            view(client, """{"path":"/","ref":"https://www.google.com/search?q=karaoke","lang":"fr"}""", PHONE_UA, "203.0.113.1")
            view(client, """{"path":"/privacy.html","ref":"https://karalo.app/","lang":"fr"}""", PHONE_UA, "203.0.113.1")
            view(client, """{"path":"/wp-admin","ref":"","lang":"en"}""", DESKTOP_UA, "203.0.113.2")
            // Crawlers and non-browser clients aren't counted.
            view(client, """{"path":"/"}""", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", "203.0.113.3")
            view(client, """{"path":"/"}""", "python-requests/2.32", "203.0.113.4")
            deps().flushStats()

            assertEquals(mapOf("/" to 1L, "/privacy.html" to 1L, "other" to 1L), counts(Metric.PAGEVIEW))
            assertEquals(mapOf("google.com" to 1L, "" to 2L), counts(Metric.PAGEVIEW_REFERRER))
            assertEquals(mapOf("fr" to 2L, "en" to 1L), counts(Metric.PAGEVIEW_LANG))
            assertEquals(mapOf("phone" to 2L, "desktop" to 1L), counts(Metric.PAGEVIEW_DEVICE))
            assertEquals(mapOf("" to 2L), counts(Metric.VISITOR))
        }

    @Test
    fun `counts from several flushes add up`() =
        testApplication {
            val deps = startApp()
            view(client, """{"path":"/"}""", PHONE_UA, "203.0.113.1")
            deps().flushStats()
            view(client, """{"path":"/"}""", PHONE_UA, "203.0.113.1")
            deps().flushStats()

            assertEquals(mapOf("/" to 2L), counts(Metric.PAGEVIEW))
        }

    @Test
    fun `karalo_app download is counted and sent on to the current APK`() =
        testApplication {
            val deps = startApp()
            val noRedirects = createClient { followRedirects = false }

            val fromApp = noRedirects.get("/download") { header(HttpHeaders.UserAgent, "okhttp/4.12.0") }
            assertEquals(HttpStatusCode.Found, fromApp.status)
            assertEquals("/download/karalo.apk", fromApp.headers[HttpHeaders.Location])
            noRedirects.get("/download/") { header(HttpHeaders.UserAgent, DESKTOP_UA) }
            noRedirects.get("/download") { header(HttpHeaders.UserAgent, "curl/8.7.1") }
            deps().flushStats()

            assertEquals(mapOf("app" to 1L, "browser" to 1L), counts(Metric.DOWNLOAD))
        }

    @Test
    fun `a new TV is counted with its app version, which later heartbeats keep up to date`() =
        testApplication {
            val deps = startApp()
            val registered = client.post("/api/tvs/tv-stats/session/ensure") { header(TV_APP_VERSION_HEADER, "0.2.0") }
            val secret = Json.parseToJsonElement(registered.bodyAsText()).jsonObject["tvSecret"]!!.jsonPrimitive.content
            assertEquals("0.2.0", appVersionOf("tv-stats"))

            client.post("/api/tvs/tv-stats/session/ensure") {
                header(HttpHeaders.Authorization, "Bearer $secret")
                header(TV_APP_VERSION_HEADER, "0.3.0")
            }
            assertEquals("0.3.0", appVersionOf("tv-stats"))
            // Builds without the header leave the stored version alone.
            client.post("/api/tvs/tv-stats/session/ensure") { header(HttpHeaders.Authorization, "Bearer $secret") }
            assertEquals("0.3.0", appVersionOf("tv-stats"))

            client.post("/api/tvs/tv-old/session/ensure")
            assertNull(appVersionOf("tv-old"))
            deps().flushStats()

            assertEquals(mapOf("0.2.0" to 1L, "" to 1L), counts(Metric.TV_REGISTERED))
            assertEquals(mapOf("" to 2L), counts(Metric.SESSION_STARTED))
        }

    @Test
    fun `guests joining are counted`() =
        testApplication {
            val deps = startApp()
            val ensure = client.post("/api/tvs/tv-party/session/ensure")
            val code = Json.parseToJsonElement(ensure.bodyAsText()).jsonObject["session"]!!.jsonObject["code"]!!.jsonPrimitive.content
            repeat(2) { i ->
                client.post("/api/sessions/$code/participants") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName":"Guest $i"}""")
                }
            }
            deps().flushStats()

            assertEquals(mapOf("" to 2L), counts(Metric.GUEST_JOINED))
        }

    private suspend fun view(
        client: HttpClient,
        body: String,
        userAgent: String,
        fromIp: String,
    ) {
        val response =
            client.post("/api/stats/view") {
                // What navigator.sendBeacon sends for a string body.
                contentType(ContentType.Text.Plain)
                header(HttpHeaders.UserAgent, userAgent)
                header("X-Forwarded-For", fromIp)
                setBody(body)
            }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private fun counts(metric: String): Map<String, Long> =
        transaction {
            DailyStats
                .selectAll()
                .where { DailyStats.metric eq metric }
                .associate { it[DailyStats.dimension] to it[DailyStats.value] }
        }

    private fun appVersionOf(tvId: String): String? =
        transaction { TvInstallations.selectAll().where { TvInstallations.id eq tvId }.single()[TvInstallations.appVersion] }
}
