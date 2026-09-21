package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.model.ParticipantJoinRequestDto
import com.karalo.backend.plugins.CommandRateLimit
import com.karalo.backend.plugins.JoinRateLimit
import com.karalo.backend.plugins.RenameRateLimit
import com.karalo.backend.plugins.SearchRateLimit
import com.karalo.backend.plugins.SessionLookupRateLimit
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Public (no auth) session bootstrap routes — what the mobile join page hits first. */
fun Route.publicSessionRoutes(deps: AppDependencies) {
    rateLimit(SessionLookupRateLimit) {
        get("/api/sessions/{code}") {
            val code = call.parameters["code"] ?: throw ApiException.Validation("Missing code")
            call.respond(deps.sessionRepository.getPublicSession(code))
        }
    }

    rateLimit(JoinRateLimit) {
        post("/api/sessions/{code}/participants") {
            val code = call.parameters["code"] ?: throw ApiException.Validation("Missing code")
            val body = call.receive<ParticipantJoinRequestDto>()
            val sessionId = deps.sessionRepository.resolveSessionIdForCode(code)
            val response = deps.participantRepository.join(sessionId, body.displayName)
            deps.broadcaster.broadcast(
                sessionId,
                "PARTICIPANT_JOINED",
                buildJsonObject {
                    put("participantId", response.participantId)
                    put("displayName", response.displayName)
                    put("participantCount", deps.participantRepository.participantCount(sessionId))
                },
            )
            call.respond(HttpStatusCode.Created, response)
        }
    }
}

/** Participant- (or TV-, for the shared queue read) authenticated routes, keyed by sessionId. */
fun Route.participantSessionRoutes(deps: AppDependencies) {
    get("/api/sessions/{sessionId}/me") {
        val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
        call.respond(deps.participantRepository.me(sessionId, call.bearerToken()))
    }

    rateLimit(RenameRateLimit) {
        patch("/api/sessions/{sessionId}/me") {
            val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
            val body = call.receive<ParticipantJoinRequestDto>()
            call.respond(deps.participantRepository.rename(sessionId, call.bearerToken(), body.displayName))
        }
    }

    rateLimit(CommandRateLimit) {
        post("/api/sessions/{sessionId}/commands/{command}") {
            val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
            val (_, displayName) = deps.participantRepository.requireParticipantAuth(sessionId, call.bearerToken())
            val command =
                when (call.parameters["command"]) {
                    "pause" -> "PAUSE"
                    "resume" -> "RESUME"
                    "skip" -> "SKIP"
                    else -> throw ApiException.Validation("Unknown command")
                }
            val commandId = UUID.randomUUID().toString()
            val delivered =
                deps.broadcaster.sendToTv(
                    sessionId,
                    "PLAYBACK_COMMAND",
                    buildJsonObject {
                        put("command", command)
                        put("commandId", commandId)
                        put("requestedByDisplayName", displayName)
                    },
                )
            if (!delivered) throw ApiException.Conflict("TV_OFFLINE")
            call.respond(HttpStatusCode.Accepted, buildJsonObject { put("commandId", commandId); put("delivered", true) })
        }
    }
}

/** Search — a participant-authenticated proxy onto [com.karalo.backend.youtube.BackendYouTubeSearch]. */
fun Route.searchRoutes(deps: AppDependencies) {
    rateLimit(SearchRateLimit) {
        get("/api/sessions/{sessionId}/search") {
            val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
            deps.participantRepository.requireParticipantAuth(sessionId, call.bearerToken())
            val query = call.request.queryParameters["q"]?.trim().orEmpty()
            if (query.isEmpty() || query.length > 100) throw ApiException.Validation("q must be 1-100 characters")
            val results = deps.youtubeSearch.search(query)
            call.respond(
                buildJsonObject {
                    put(
                        "results",
                        buildJsonArray {
                            results.forEach { r ->
                                add(
                                    buildJsonObject {
                                        put("videoId", r.videoId)
                                        put("title", r.title)
                                        put("channelName", r.channelName)
                                        put("thumbnailUrl", r.thumbnailUrl)
                                        put("durationSeconds", r.durationSeconds)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }
}
