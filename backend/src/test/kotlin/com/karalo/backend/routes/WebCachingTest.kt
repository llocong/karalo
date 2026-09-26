package com.karalo.backend.routes

import com.karalo.backend.config.AppConfig
import com.karalo.backend.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The web app's pages, scripts and styles are revalidated on every visit, and unchanged ones cost a 304. */
class WebCachingTest {
    private fun testConfig() =
        AppConfig(dbPath = File.createTempFile("karalo-caching-test-", ".db").apply { deleteOnExit() }.absolutePath)

    @Test
    fun `scripts, styles and pages carry an ETag and revalidate to 304`() =
        testApplication {
            application { module(testConfig()) }
            for (path in listOf("/shared.js", "/shared.css", "/search.html", "/join/ABCD1234")) {
                val first = client.get(path)
                assertEquals(HttpStatusCode.OK, first.status, path)
                assertEquals("no-cache", first.headers[HttpHeaders.CacheControl], path)
                val etag = assertNotNull(first.headers[HttpHeaders.ETag], path)

                val again = client.get(path) { header(HttpHeaders.IfNoneMatch, etag) }
                assertEquals(HttpStatusCode.NotModified, again.status, path)
            }
        }

    @Test
    fun `images get no Cache-Control from the backend - Caddy sets their lifetime`() =
        testApplication {
            application { module(testConfig()) }
            assertNull(client.get("/images/playlists/pop.webp").headers[HttpHeaders.CacheControl])
        }
}
