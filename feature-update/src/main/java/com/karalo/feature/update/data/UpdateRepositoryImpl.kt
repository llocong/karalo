package com.karalo.feature.update.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karalo.core.common.di.ApplicationScope
import com.karalo.core.common.di.IoDispatcher
import com.karalo.feature.update.di.UpdateDataStore
import com.karalo.feature.update.domain.InstallResult
import com.karalo.feature.update.domain.UpdateRelease
import com.karalo.feature.update.domain.UpdateRepository
import com.karalo.feature.update.domain.UpdateState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Where to look for updates, and whether this build may update itself at all (see build.gradle.kts). */
data class UpdateConfig(
    val manifestUrl: String,
    val enabled: Boolean,
    /** Where downloads go; the FileProvider serves `files/updates/` (res/xml/karalo_update_paths.xml). */
    val updatesDir: File,
)

private val KEY_RELEASE = stringPreferencesKey("release_manifest")
private val KEY_READY_APK = stringPreferencesKey("ready_apk")
private val KEY_READY_VERSION = longPreferencesKey("ready_version_code")

private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_INTERVAL_MS = 250L

/**
 * The app's next version, from check to install. One per process, so a download keeps going
 * (on the application scope) while the user sings, and every screen sees the same state.
 *
 * What survives a restart, in DataStore: the last newer release seen (so the Settings dot is
 * still there offline) and a finished download. Both are dropped once the installed version has
 * caught up, which is how the dot disappears after an update.
 */
@Singleton
class UpdateRepositoryImpl
    @Inject
    internal constructor(
        private val client: OkHttpClient,
        @UpdateDataStore private val dataStore: DataStore<Preferences>,
        private val appPackage: AppPackage,
        private val config: UpdateConfig,
        @ApplicationScope private val scope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : UpdateRepository {
        private val _state = MutableStateFlow<UpdateState>(UpdateState.UpToDate)
        override val state: StateFlow<UpdateState> = _state.asStateFlow()

        override val installedVersion: String get() = appPackage.versionName

        private val mutex = Mutex()
        private var restored = false

        /** Reads what a previous run left behind. Must hold [mutex]. */
        private suspend fun restoreOnce() {
            if (restored) return
            restored = true
            val prefs = withContext(ioDispatcher) { dataStore.data.first() }
            val release = prefs[KEY_RELEASE]?.let { runCatching { UpdateManifest.parse(it).toRelease() }.getOrNull() }
            val readyApk = prefs[KEY_READY_APK]?.let(::File)
            val installed = appPackage.versionCode
            _state.value =
                when {
                    release == null || release.versionCode <= installed -> {
                        forgetEverything()
                        UpdateState.UpToDate
                    }
                    readyApk != null && prefs[KEY_READY_VERSION] == release.versionCode && readyApk.exists() ->
                        UpdateState.ReadyToRestart(release, readyApk)
                    else -> UpdateState.Available(release)
                }
        }

        override suspend fun check() {
            if (!config.enabled) return
            mutex.withLock {
                restoreOnce()
                // A newer version showing up mid-download or once one is ready waits for the next
                // update: the one in hand is finished first.
                if (_state.value is UpdateState.Downloading || _state.value is UpdateState.ReadyToRestart) return
                val text = fetchManifest() ?: return // offline or a bad file: keep what we had
                val release = runCatching { UpdateManifest.parse(text).toRelease() }.getOrNull() ?: return
                if (release.versionCode > appPackage.versionCode) {
                    val current = _state.value
                    if (current !is UpdateState.Available || current.release != release) {
                        _state.value = UpdateState.Available(release)
                    }
                    withContext(ioDispatcher) { dataStore.edit { it[KEY_RELEASE] = text } }
                } else {
                    _state.value = UpdateState.UpToDate
                    forgetEverything()
                }
            }
        }

        private suspend fun fetchManifest(): String? =
            withContext(ioDispatcher) {
                val request =
                    Request
                        .Builder()
                        .url(config.manifestUrl)
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                }.getOrNull()
            }

        override fun startDownload() {
            val available = _state.value as? UpdateState.Available ?: return
            val release = available.release
            _state.value = UpdateState.Downloading(release, 0, release.sizeBytes ?: 0)
            scope.launch(ioDispatcher) {
                val apk =
                    try {
                        download(release)
                    } catch (_: IOException) {
                        null // no network, or the connection dropped
                    } catch (_: IllegalArgumentException) {
                        null // a malformed apkUrl
                    }
                mutex.withLock {
                    if (apk == null) {
                        _state.value = UpdateState.Available(release, failed = true)
                        return@withLock
                    }
                    dataStore.edit {
                        it[KEY_READY_APK] = apk.absolutePath
                        it[KEY_READY_VERSION] = release.versionCode
                    }
                    _state.value = UpdateState.ReadyToRestart(release, apk)
                }
            }
        }

        /** Streams the APK to disk, checking its sha256; returns the finished file or null. */
        private fun download(release: UpdateRelease): File? {
            val dir = config.updatesDir.apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // an older download or a half-finished one
            val partial = File(dir, "karalo-${release.version}.apk.part")
            val digest = MessageDigest.getInstance("SHA-256")
            val request = Request.Builder().url(release.apkUrl).build()
            val complete =
                client.newCall(request).execute().use { response ->
                    val body = response.body?.takeIf { response.isSuccessful } ?: return@use false
                    save(body, partial, digest, release)
                    true
                }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!complete || sha != release.sha256) {
                partial.delete()
                return null
            }
            val apk = File(dir, "karalo-${release.version}.apk")
            return if (partial.renameTo(apk)) apk else null
        }

        /** Writes the download to [file], feeding [digest] and reporting progress a few times a second. */
        private fun save(
            body: ResponseBody,
            file: File,
            digest: MessageDigest,
            release: UpdateRelease,
        ) {
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes ?: 0
            body.byteStream().use { input ->
                file.outputStream().use { output -> copy(input, output, digest, release, total) }
            }
        }

        private fun copy(
            input: InputStream,
            output: OutputStream,
            digest: MessageDigest,
            release: UpdateRelease,
            total: Long,
        ) {
            val buffer = ByteArray(BUFFER_SIZE)
            var downloaded = 0L
            var lastReport = 0L
            var read = input.read(buffer)
            while (read >= 0) {
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                downloaded += read
                val now = System.currentTimeMillis()
                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                    lastReport = now
                    _state.value = UpdateState.Downloading(release, downloaded, total)
                }
                read = input.read(buffer)
            }
            _state.value = UpdateState.Downloading(release, downloaded, maxOf(total, downloaded))
        }

        override fun install(): InstallResult {
            val ready = _state.value as? UpdateState.ReadyToRestart ?: return InstallResult.FAILED
            if (!ready.apk.exists()) {
                _state.value = UpdateState.Available(ready.release, failed = true)
                return InstallResult.FAILED
            }
            return appPackage.install(ready.apk)
        }

        private suspend fun forgetEverything() {
            withContext(ioDispatcher) {
                dataStore.edit { it.clear() }
                config.updatesDir.listFiles()?.forEach { it.delete() }
            }
        }
    }
