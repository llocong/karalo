package com.karalo.youtubeclient.internal

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloaderAdapterTest {
    private val server = MockWebServer()
    private lateinit var adapter: NewPipeDownloaderAdapter

    @BeforeEach
    fun setUp() {
        server.start()
        adapter = NewPipeDownloaderAdapter(OkHttpClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `execute sends the method, url and headers, and maps the response back`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val request =
            Request
                .newBuilder()
                .get(server.url("/videos?id=abc").toString())
                .setHeader("X-Test", "value")
                .build()
        val response = adapter.execute(request)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/videos?id=abc", recorded.path)
        assertEquals("value", recorded.getHeader("X-Test"))
        assertEquals(200, response.responseCode())
        assertEquals("""{"ok":true}""", response.responseBody())
    }

    @Test
    fun `execute sends a POST body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val request =
            Request
                .newBuilder()
                .post(server.url("/search").toString(), "query=abc".toByteArray())
                .build()
        adapter.execute(request)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("query=abc", recorded.body.readUtf8())
    }

    @Test
    fun `execute throws ReCaptchaException when the server responds 429`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val request = Request.newBuilder().get(server.url("/videos").toString()).build()

        assertThrows(ReCaptchaException::class.java) { adapter.execute(request) }
    }
}
