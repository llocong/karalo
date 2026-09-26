package com.karalo.backend

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.ParticipantRepository
import com.karalo.backend.db.PlayHistoryRepository
import com.karalo.backend.db.QueueRepository
import com.karalo.backend.db.SessionRepository
import com.karalo.backend.db.onSessionEnded
import com.karalo.backend.db.onSessionStarted
import com.karalo.backend.db.onSongPlayed
import com.karalo.backend.domain.SESSION_ENDED
import com.karalo.backend.realtime.SessionBroadcaster
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
import org.slf4j.LoggerFactory
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

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    }

    /** Writes pending usage counts; also called on shutdown (Application.kt). Never throws. */
    fun flushStats() {
        runCatching { stats.flush() }.onFailure { log.warn("Couldn't save usage statistics", it) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AppDependencies::class.java)
    }
}
