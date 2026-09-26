package com.karalo.feature.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.karalo.feature.update.data.AppPackage
import com.karalo.feature.update.data.UpdateConfig
import com.karalo.feature.update.data.UpdateRepositoryImpl
import com.karalo.feature.update.domain.InstallResult
import com.karalo.feature.update.domain.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

private const val SAMPLE_NOTES =
    """"releaseNotes":{"new":["Updates install from Settings."],"improved":[],"fixed":["A fix."]},"""
private val APK_BYTES = ByteArray(200_000) { (it % 251).toByte() }
private val APK_SHA = MessageDigest.getInstance("SHA-256").digest(APK_BYTES).joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    private val server = MockWebServer()
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val appPackage = FakeAppPackage(versionCode = 3000)

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    /** A new repository over the same storage, like the app starting again. */
    private fun repository(enabled: Boolean = true) =
        UpdateRepositoryImpl(
            client = OkHttpClient(),
            dataStore = dataStore,
            appPackage = appPackage,
            config = UpdateConfig(server.url("/download/latest.json").toString(), enabled, File(tempDir, "updates")),
            scope = testScope.backgroundScope,
            ioDispatcher = dispatcher,
        )

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) { File(tempDir, "updates.preferences_pb") }
    }

    private fun manifest(
        versionCode: Long = 4000,
        version: String = "0.4.0",
        sha256: String = APK_SHA,
        notes: String = SAMPLE_NOTES,
    ) = MockResponse().setBody(
        """{"versionCode":$versionCode,"versionName":"$version","version":"$version","releaseDate":"2026-10-02",""" +
            """"sizeBytes":${APK_BYTES.size},$notes"apkUrl":"${server.url("/download/karalo-$version.apk")}",""" +
            """"sha256":"$sha256","notes":"","releasedAt":"2026-10-02T18:00:00Z"}""",
    )

    private fun apk() = MockResponse().setBody(Buffer().write(APK_BYTES))

    @Test
    fun `a newer version is available, with its notes and date`() =
        testScope.runTest {
            server.enqueue(manifest())
            val repo = repository()
            repo.check()

            val state = assertInstanceOf(UpdateState.Available::class.java, repo.state.value)
            assertEquals("0.4.0", state.release.version)
            assertEquals(LocalDate.of(2026, 10, 2), state.release.releaseDate)
            assertEquals(listOf("Updates install from Settings."), state.release.notes.new)
            assertEquals(listOf("A fix."), state.release.notes.fixed)
        }

    @Test
    fun `the installed version, or an older one, is up to date`() =
        testScope.runTest {
            server.enqueue(manifest(versionCode = 3000, version = "0.3.0"))
            val repo = repository()
            repo.check()
            assertEquals(UpdateState.UpToDate, repo.state.value)
        }

    @Test
    fun `a manifest from before the updater, without notes, still parses`() =
        testScope.runTest {
            server.enqueue(manifest(notes = ""))
            val repo = repository()
            repo.check()
            assertTrue(assertInstanceOf(UpdateState.Available::class.java, repo.state.value).release.notes.isEmpty)
        }

    @Test
    fun `offline, the last known state is kept, even across a restart`() =
        testScope.runTest {
            server.enqueue(manifest())
            repository().check()

            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val restarted = repository()
            restarted.check()
            assertInstanceOf(UpdateState.Available::class.java, restarted.state.value)
        }

    @Test
    fun `a download finishes ready to restart, and install hands it to the system`() =
        testScope.runTest {
            server.enqueue(manifest())
            server.enqueue(apk())
            val repo = repository()
            repo.check()
            repo.startDownload()
            assertInstanceOf(UpdateState.Downloading::class.java, repo.state.value)
            awaitDownload(repo)

            val ready = assertInstanceOf(UpdateState.ReadyToRestart::class.java, repo.state.value)
            assertTrue(ready.apk.readBytes().contentEquals(APK_BYTES))
            assertEquals(InstallResult.STARTED, repo.install())
            assertEquals(ready.apk, appPackage.installed)
        }

    @Test
    fun `a download that doesn't match its checksum fails back to available`() =
        testScope.runTest {
            server.enqueue(manifest(sha256 = "0".repeat(64)))
            server.enqueue(apk())
            val repo = repository()
            repo.check()
            repo.startDownload()
            awaitDownload(repo)

            assertTrue(assertInstanceOf(UpdateState.Available::class.java, repo.state.value).failed)
            assertFalse(File(tempDir, "updates").listFiles().orEmpty().any { it.name.endsWith(".apk") })
        }

    @Test
    fun `a dropped connection mid-download fails back to available`() =
        testScope.runTest {
            server.enqueue(manifest())
            server.enqueue(apk().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            val repo = repository()
            repo.check()
            repo.startDownload()
            awaitDownload(repo)
            assertTrue(assertInstanceOf(UpdateState.Available::class.java, repo.state.value).failed)
        }

    @Test
    fun `a newer version showing up once one is ready waits until after the restart`() =
        testScope.runTest {
            server.enqueue(manifest())
            server.enqueue(apk())
            val repo = repository()
            repo.check()
            repo.startDownload()
            awaitDownload(repo)

            server.enqueue(manifest(versionCode = 5000, version = "0.5.0"))
            repo.check()
            assertEquals(
                "0.4.0",
                assertInstanceOf(UpdateState.ReadyToRestart::class.java, repo.state.value).release.version,
            )
        }

    @Test
    fun `a ready download is still ready after a restart`() =
        testScope.runTest {
            server.enqueue(manifest())
            server.enqueue(apk())
            val repo = repository()
            repo.check()
            repo.startDownload()
            awaitDownload(repo)

            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val restarted = repository()
            restarted.check()
            assertInstanceOf(UpdateState.ReadyToRestart::class.java, restarted.state.value)
        }

    @Test
    fun `once the update is installed, it's up to date and the download is deleted`() =
        testScope.runTest {
            server.enqueue(manifest())
            server.enqueue(apk())
            val repo = repository()
            repo.check()
            repo.startDownload()
            awaitDownload(repo)

            appPackage.versionCode = 4000 // the new version is now installed
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val updated = repository()
            updated.check()
            assertEquals(UpdateState.UpToDate, updated.state.value)
            assertTrue(File(tempDir, "updates").listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `a build without the self-updater never checks`() =
        testScope.runTest {
            val repo = repository(enabled = false)
            repo.check()
            assertEquals(0, server.requestCount)
            assertEquals(UpdateState.UpToDate, repo.state.value)
        }

    /**
     * Waits (in real time) for the download to finish either way: DataStore writes on its own
     * threads, so skipping ahead on the test clock alone returns before the result is saved.
     */
    private suspend fun awaitDownload(repo: UpdateRepositoryImpl) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { repo.state.first { it !is UpdateState.Downloading } }
        }
    }

    private class FakeAppPackage(
        override var versionCode: Long,
    ) : AppPackage {
        override val versionName = "0.3.0"
        var installed: File? = null

        override fun install(apk: File): InstallResult {
            installed = apk
            return InstallResult.STARTED
        }
    }
}
