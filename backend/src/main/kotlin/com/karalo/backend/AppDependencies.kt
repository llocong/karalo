package com.karalo.backend

import com.karalo.backend.admin.AdminAuth
import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.AdminRepository
import com.karalo.backend.db.ParticipantRepository
import com.karalo.backend.db.PlayHistoryRepository
import com.karalo.backend.db.QueueRepository
import com.karalo.backend.db.SessionRepository
import com.karalo.backend.db.onSessionEnded
import com.karalo.backend.db.onSessionStarted
import com.karalo.backend.db.onSongPlayed
import com.karalo.backend.db.tables.AppSettings
import com.karalo.backend.domain.SESSION_ENDED
import com.karalo.backend.realtime.SessionBroadcaster
import com.karalo.backend.security.AlertSender
import com.karalo.backend.security.SecurityMonitor
import com.karalo.backend.stats.GitHubReleases
import com.karalo.backend.stats.Metric
import com.karalo.backend.stats.StatsRecorder
import com.karalo.backend.youtube.BackendYouTubeSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Plain manual DI — this app is small enough that a DI framework would be pure ceremony. */
class AppDependencies(
    val config: AppConfig,
) {
    val sessionRepository = SessionRepository(tvRegistrationKey = config.tvRegistrationKey)
    val participantRepository = ParticipantRepository()
    val queueRepository = QueueRepository(sessionRepository)
    val playHistoryRepository = PlayHistoryRepository()
    val broadcaster = SessionBroadcaster(com.karalo.backend.plugins.appJson)
    val youtubeSearch = BackendYouTubeSearch(OkHttpClient())
    val stats = StatsRecorder()
    val adminAuth = AdminAuth(config.adminPasswordHash)
    val adminRepository = AdminRepository(sessionRepository)

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val alerts = config.alertNtfyUrl?.let { AlertSender(it, config.publicBaseUrl, OkHttpClient(), backgroundScope) }
    val security = SecurityMonitor(securityKey(), alerts, sessionCodeOf = sessionRepository::codeOf)

    init {
        // A session that ends (its TV went quiet) drops its open phones right away, so they land
        // on the "session over" page instead of waiting for their next request.
        onSessionEnded = { sessionId -> backgroundScope.launch { broadcaster.closePhones(sessionId, SESSION_ENDED) } }
        onSessionStarted = { stats.count(Metric.SESSION_STARTED) }
        onSongPlayed = { stats.count(Metric.SONG_PLAYED) }
        backgroundScope.launch {
            while (isActive) {
                delay(1.minutes)
                flushStats()
            }
        }
        config.githubRepo?.let { repo ->
            val github = GitHubReleases(OkHttpClient(), repo)
            backgroundScope.launch {
                while (isActive) {
                    github.apkDownloadTotal()?.let { stats.setGauge(Metric.GITHUB_DOWNLOADS_TOTAL, it) }
                    delay(1.hours)
                }
            }
        }
    }

    /** Writes pending usage counts; also called on shutdown (Application.kt). Never throws. */
    fun flushStats() {
        runCatching { stats.flush() }.onFailure { log.warn("Couldn't save usage statistics", it) }
        runCatching { security.flush() }.onFailure { log.warn("Couldn't save security events", it) }
    }

    /** The key that turns IP addresses into security-event sources, made once and kept in app_settings. */
    private fun securityKey(): ByteArray =
        transaction {
            val stored = AppSettings.selectAll().where { AppSettings.key eq SECURITY_KEY }.singleOrNull()?.get(AppSettings.value)
            val encoded =
                stored ?: Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes)).also { value ->
                    AppSettings.insert {
                        it[key] = SECURITY_KEY
                        it[this.value] = value
                    }
                }
            Base64.getDecoder().decode(encoded)
        }

    private companion object {
        val log = LoggerFactory.getLogger(AppDependencies::class.java)
        const val SECURITY_KEY = "security_source_key"
    }
}
