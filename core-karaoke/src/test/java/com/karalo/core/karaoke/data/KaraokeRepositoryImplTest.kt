package com.karalo.core.karaoke.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.result.AppResult
import com.karalo.core.karaoke.data.remote.KaraokeApi
import com.karalo.core.karaoke.data.remote.dto.QueueSnapshotDto
import com.karalo.core.karaoke.data.remote.dto.SessionEnsureResponseDto
import com.karalo.core.karaoke.data.remote.dto.SessionSummaryDto
import com.karalo.core.karaoke.data.ws.KaraokeWebSocketClient
import com.karalo.core.karaoke.domain.TvInstallationIdProvider
import com.karalo.core.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private fun sessionSummary(id: String = "session-1") =
    SessionSummaryDto(
        id = id,
        code = "ABCDEFGH",
        joinUrl = "http://192.168.1.100:8080/join/ABCDEFGH",
        playbackState = "IDLE",
        nowPlaying = null,
        participantCount = 0,
        queueLength = 0,
    )

/**
 * Covers [KaraokeRepositoryImpl.ensureSession]'s tvSecret persistence -- the backend's
 * `session/ensure` is trust-on-first-use, so every call after the very first one for a given TV
 * installation must present the secret issued then. [FakeDataStore] (shared with
 * `TvInstallationIdProviderImplTest`) plays the role of the on-disk store surviving a process
 * restart, which a plain in-memory field, by construction, cannot survive -- this covers a real
 * bug found by manually restarting the app against a live backend during feature verification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KaraokeRepositoryImplTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val api = mockk<KaraokeApi>()
    private val webSocketClient = mockk<KaraokeWebSocketClient>()
    private val tvInstallationIdProvider = mockk<TvInstallationIdProvider>()
    private val logger = mockk<Logger>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private fun repository(dataStore: DataStore<Preferences>): KaraokeRepositoryImpl {
        val appScope: CoroutineScope = CoroutineScope(mainDispatcherExtension.testDispatcher)
        coEvery { tvInstallationIdProvider.getOrCreate() } returns "tv-1"
        // The WS loop is fire-and-forget from ensureSession()'s point of view; suspending it
        // forever here keeps it out of this test's way without it ever actually reconnecting.
        coEvery {
            webSocketClient.connectAndAwaitClose(any(), any(), any(), any())
        } coAnswers { awaitCancellation() }
        coEvery { api.fetchQueue(any(), any()) } returns
            AppResult.Success(QueueSnapshotDto("IDLE", null, emptyList()))
        return KaraokeRepositoryImpl(api, webSocketClient, tvInstallationIdProvider, json, logger, appScope, dataStore)
    }

    @Test
    fun `a freshly issued tvSecret is persisted to the data store`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val dataStore = FakeDataStore()
            coEvery { api.ensureSession("tv-1", null) } returns
                AppResult.Success(
                    SessionEnsureResponseDto(tvSecret = "fresh-secret", session = sessionSummary()),
                )

            repository(dataStore).ensureSession()
            advanceUntilIdle()

            assertEquals("fresh-secret", dataStore.data.value[TV_SECRET_KEY])
        }

    @Test
    fun `a secret persisted from a prior process is reused rather than sent as null`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val dataStore = FakeDataStore()
            dataStore.updateData { it.toMutablePreferences().apply { this[TV_SECRET_KEY] = "old-secret" } }
            coEvery { api.ensureSession("tv-1", "old-secret") } returns
                AppResult.Success(
                    SessionEnsureResponseDto(tvSecret = null, session = sessionSummary()),
                )

            repository(dataStore).ensureSession()
            advanceUntilIdle()

            coVerify { api.ensureSession("tv-1", "old-secret") }
        }

    @Test
    fun `playNowEnd fails cleanly with no cached session rather than crashing`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val result = repository(FakeDataStore()).playNowEnd()

            assert(result is AppResult.Failure)
        }
}
