package com.karalo.core.karaoke.data.remote

import com.karalo.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KaraokeApiHistoryTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun api() =
        KaraokeApiImpl(
            OkHttpClient(),
            Json { ignoreUnknownKeys = true },
            Dispatchers.Unconfined,
            restBaseUrl = server.url("").toString().removeSuffix("/"),
            tvRegistrationKey = "",
        )

    @Test
    fun `history by date sends the sort, cursor and limit with the TV secret`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"id":"1","videoId":"abcdefghijk","title":"T","channelName":"C",""" +
                        """"thumbnailUrl":null,"playedAt":"2026-09-24T01:00:00Z",""" +
                        """"nightStartedAt":"2026-09-24T01:00:00Z"}],""" +
                        """"nextCursor":"next+1","paused":true}""",
                ),
            )

            val result = api().fetchHistoryByDate("s1", "secret", before = "a b/c", limit = 50)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/sessions/s1/history?sort=date&before=a%20b%2Fc&limit=50", request.path)
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            val page = (result as AppResult.Success).data
            assertEquals("next+1", page.nextCursor)
            assertTrue(page.paused)
            assertEquals(1, page.items.size)
        }

    @Test
    fun `history by date omits the cursor on the first page`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"items":[],"nextCursor":null,"paused":false}"""))
            api().fetchHistoryByDate("s1", "secret", before = null, limit = 50)
            assertEquals("/api/sessions/s1/history?sort=date&limit=50", server.takeRequest().path)
        }

    @Test
    fun `most played sends the offset`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"items":[],"nextOffset":null,"paused":false}"""))
            api().fetchMostPlayed("s1", "secret", offset = 100, limit = 50)
            assertEquals("/api/sessions/s1/history?sort=most_played&offset=100&limit=50", server.takeRequest().path)
        }

    @Test
    fun `pausing is a PUT with the new state`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"paused":true}"""))
            val result = api().setHistoryPaused("s1", "secret", paused = true)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/sessions/s1/history/paused", request.path)
            assertEquals("""{"paused":true}""", request.body.readUtf8())
            assertEquals(AppResult.Success(true), result)
        }

    @Test
    fun `clearing is a DELETE whose empty 204 is a success`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))
            val result = api().clearHistory("s1", "secret")

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/sessions/s1/history", request.path)
            assertEquals(AppResult.Success(Unit), result)
        }
}
