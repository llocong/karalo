package com.karalo.core.karaoke.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KaraokeApiImplTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() {
        server.start()
        server.enqueue(MockResponse().setResponseCode(201).setBody(ENSURE_RESPONSE))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun api(
        tvRegistrationKey: String = "",
        appVersion: String = "",
    ) = KaraokeApiImpl(
        OkHttpClient(),
        Json { ignoreUnknownKeys = true },
        Dispatchers.Unconfined,
        restBaseUrl = server.url("").toString().removeSuffix("/"),
        tvRegistrationKey = tvRegistrationKey,
        appVersion = appVersion,
    )

    @Test
    fun `ensureSession sends the registration key when the build has one`() =
        runTest {
            api(tvRegistrationKey = "the-key").ensureSession("tv-1", tvSecret = null)

            val request = server.takeRequest()
            assertEquals("/api/tvs/tv-1/session/ensure", request.path)
            assertEquals("the-key", request.getHeader("X-Karalo-Registration-Key"))
        }

    @Test
    fun `ensureSession sends no registration key header when the build has none`() =
        runTest {
            api(tvRegistrationKey = "").ensureSession("tv-1", tvSecret = null)

            assertNull(server.takeRequest().getHeader("X-Karalo-Registration-Key"))
        }

    @Test
    fun `ensureSession reports the app version`() =
        runTest {
            api(appVersion = "0.3.0").ensureSession("tv-1", tvSecret = null)

            assertEquals("0.3.0", server.takeRequest().getHeader("X-Karalo-App-Version"))
        }

    private companion object {
        const val ENSURE_RESPONSE =
            """{"tvSecret":"s","session":{"id":"id","code":"ABCD1234",""" +
                """"joinUrl":"https://example.com/join/ABCD1234","playbackState":"IDLE",""" +
                """"nowPlaying":null,"participantCount":0,"queueLength":0}}"""
    }
}
