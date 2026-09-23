package com.karalo.backend

import com.karalo.backend.config.AppConfig
import com.karalo.backend.db.ParticipantRepository
import com.karalo.backend.db.QueueRepository
import com.karalo.backend.db.SessionRepository
import com.karalo.backend.realtime.SessionBroadcaster
import com.karalo.backend.youtube.BackendYouTubeSearch
import okhttp3.OkHttpClient

/** Plain manual DI — this app is small enough that a DI framework would be pure ceremony. */
class AppDependencies(
    val config: AppConfig,
) {
    val sessionRepository = SessionRepository(tvRegistrationKey = config.tvRegistrationKey)
    val participantRepository = ParticipantRepository()
    val queueRepository = QueueRepository(sessionRepository)
    val broadcaster = SessionBroadcaster(com.karalo.backend.plugins.appJson)
    val youtubeSearch = BackendYouTubeSearch(OkHttpClient())
}
