package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.model.AddQueueItemRequestDto
import com.karalo.backend.domain.model.QueueSnapshotDto
import com.karalo.backend.domain.model.ReorderRequestDto
import com.karalo.backend.plugins.QueueAddRateLimit
import com.karalo.backend.plugins.appJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

fun Route.queueRoutes(deps: AppDependencies) {
    get("/api/sessions/{sessionId}/queue") {
        val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
        val ok = deps.participantRepository.isValidParticipantOrTv(sessionId, call.bearerToken(), deps.sessionRepository)
        if (!ok) throw ApiException.Unauthorized()
        val nowPlaying = deps.sessionRepository.getNowPlaying(sessionId)
        call.respond(
            QueueSnapshotDto(
                playbackState = deps.sessionRepository.getPlaybackState(sessionId),
                nowPlaying = nowPlaying,
                // The current QUEUE-sourced now-playing item stays PENDING (see
                // SessionRepository.syncNowPlayingToQueueHead's doc) until it's actually consumed
                // -- that's what lets Play-Now leave the queue's head untouched -- but showing it
                // a second time in "upcoming" would be confusing on the mobile Queue screen, so
                // it's filtered out here at the presentation boundary only.
                queue = deps.queueRepository.listPending(sessionId).filterNot { it.id == nowPlaying?.queueItemId },
            ),
        )
    }

    rateLimit(QueueAddRateLimit) {
        post("/api/sessions/{sessionId}/queue") {
            val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
            val (participantId, displayName) = deps.participantRepository.requireParticipantAuth(sessionId, call.bearerToken())
            val body = call.receive<AddQueueItemRequestDto>()
            val item =
                deps.queueRepository.add(
                    sessionId = sessionId,
                    participantId = participantId,
                    participantName = displayName,
                    videoId = body.videoId,
                    title = body.title,
                    channelName = body.channelName,
                    thumbnailUrl = body.thumbnailUrl,
                    durationSeconds = body.durationSeconds,
                )
            deps.broadcaster.broadcast(sessionId, "QUEUE_ITEM_ADDED", appJson.encodeToJsonElement(item))
            deps.broadcaster.broadcast(sessionId, "NOW_PLAYING_CHANGED", nowPlayingPayload(deps.sessionRepository.getNowPlaying(sessionId)))
            call.respond(HttpStatusCode.Created, item)
        }
    }

    delete("/api/sessions/{sessionId}/queue/{queueItemId}") {
        val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
        val queueItemId = call.parameters["queueItemId"] ?: throw ApiException.Validation("Missing queueItemId")
        deps.participantRepository.requireParticipantAuth(sessionId, call.bearerToken())
        deps.queueRepository.delete(sessionId, queueItemId)
        deps.broadcaster.broadcast(sessionId, "QUEUE_ITEM_REMOVED", buildJsonObject { put("queueItemId", queueItemId) })
        call.respond(HttpStatusCode.NoContent)
    }

    patch("/api/sessions/{sessionId}/queue/reorder") {
        val sessionId = call.parameters["sessionId"] ?: throw ApiException.Validation("Missing sessionId")
        deps.participantRepository.requireParticipantAuth(sessionId, call.bearerToken())
        val body = call.receive<ReorderRequestDto>()
        val reordered = deps.queueRepository.reorder(sessionId, body.orderedQueueItemIds)
        deps.broadcaster.broadcast(sessionId, "QUEUE_UPDATED", appJson.encodeToJsonElement(reordered))
        call.respond(reordered)
    }
}
