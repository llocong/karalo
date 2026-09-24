package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.model.ConsumeNextRequestDto
import com.karalo.backend.domain.model.PlayNowStartRequestDto
import com.karalo.backend.domain.model.PlaybackStateRequestDto
import com.karalo.backend.domain.model.ThemeDto
import com.karalo.backend.plugins.appJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** Header a TV sends on `session/ensure` to register; see `AppConfig.tvRegistrationKey`. */
const val TV_REGISTRATION_KEY_HEADER = "X-Karalo-Registration-Key"

internal fun requireSessionId(call: io.ktor.server.application.ApplicationCall) =
    call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")

/** Routes only the TV (holding the session's `tvSecret`) is ever authorized to call. */
fun Route.tvRoutes(deps: AppDependencies) {
    post("/api/tvs/{tvId}/session/ensure") {
        val tvId = call.parameters["tvId"] ?: throw ApiException.Validation("Missing tvId")
        val response =
            deps.sessionRepository.ensureSession(
                tvId,
                call.bearerToken(),
                presentedRegistrationKey = call.request.headers[TV_REGISTRATION_KEY_HEADER],
            )
        val status = if (response.tvSecret != null) HttpStatusCode.Created else HttpStatusCode.OK
        call.respond(status, response)
    }

    post("/api/sessions/{sessionId}/queue/consume-next") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        val body = runCatching { call.receive<ConsumeNextRequestDto>() }.getOrDefault(ConsumeNextRequestDto())
        val nowPlaying = deps.queueRepository.consumeNext(sessionId, skipped = body.reason == "SKIPPED")
        deps.broadcaster.broadcast(sessionId, "QUEUE_UPDATED", appJson.encodeToJsonElement(deps.queueRepository.listPending(sessionId)))
        deps.broadcaster.broadcast(sessionId, "NOW_PLAYING_CHANGED", nowPlayingPayload(nowPlaying))
        call.respond(nowPlayingPayload(nowPlaying))
    }

    post("/api/sessions/{sessionId}/play-now/start") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        val body = call.receive<PlayNowStartRequestDto>()
        val nowPlaying =
            deps.sessionRepository.playNowStart(
                sessionId,
                body.videoId,
                body.title,
                body.channelName,
                body.thumbnailUrl,
                body.durationSeconds,
            )
        deps.broadcaster.broadcast(sessionId, "NOW_PLAYING_CHANGED", nowPlayingPayload(nowPlaying))
        call.respond(nowPlayingPayload(nowPlaying))
    }

    post("/api/sessions/{sessionId}/play-now/end") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        val nowPlaying = deps.sessionRepository.playNowEnd(sessionId)
        deps.broadcaster.broadcast(sessionId, "NOW_PLAYING_CHANGED", nowPlayingPayload(nowPlaying))
        call.respond(nowPlayingPayload(nowPlaying))
    }

    post("/api/sessions/{sessionId}/playback-state") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        val body = call.receive<PlaybackStateRequestDto>()
        deps.sessionRepository.setPlaybackState(sessionId, body.playbackState)
        deps.broadcaster.broadcast(sessionId, "SESSION_UPDATED", buildJsonObject { put("playbackState", body.playbackState) })
        call.respond(HttpStatusCode.OK, buildJsonObject { put("ok", true) })
    }

    // The host's seasonal theme pick from TV Settings. Phones re-fetch the queue snapshot (which
    // carries the theme) on SESSION_UPDATED, so the broadcast is all it takes to restyle them.
    put("/api/sessions/{sessionId}/theme") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        val body = call.receive<ThemeDto>()
        val theme = deps.sessionRepository.setTheme(sessionId, body.theme)
        deps.broadcaster.broadcast(sessionId, "SESSION_UPDATED", buildJsonObject { put("theme", theme) })
        call.respond(ThemeDto(theme))
    }
}
