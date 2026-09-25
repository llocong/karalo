package com.karalo.backend

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.ParticipantRepository
import com.karalo.backend.db.PlayHistoryRepository
import com.karalo.backend.db.QueueRepository
import com.karalo.backend.db.SessionRepository
import com.karalo.backend.db.onSessionEnded
import com.karalo.backend.domain.SESSION_ENDED
import com.karalo.backend.realtime.SessionBroadcaster
import com.karalo.backend.youtube.BackendYouTubeSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // A session that ends (its TV went quiet) drops its open phones right away, so they land
        // on the "session over" page instead of waiting for their next request.
        onSessionEnded = { sessionId -> backgroundScope.launch { broadcaster.closePhones(sessionId, SESSION_ENDED) } }
    }
}
