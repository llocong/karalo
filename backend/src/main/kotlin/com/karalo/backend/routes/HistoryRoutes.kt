package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.model.HistoryPausedDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

private const val DEFAULT_HISTORY_PAGE_SIZE = 50

/** The TV's History page: browse, pause/resume and clear the session's song history. TV-only. */
fun Route.historyRoutes(deps: AppDependencies) {
    get("/api/sessions/{sessionId}/history") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        val params = call.request.queryParameters
        val limit = params["limit"]?.toIntOrNull() ?: DEFAULT_HISTORY_PAGE_SIZE
        when (params["sort"] ?: "date") {
            "date" -> call.respond(deps.playHistoryRepository.byDate(sessionId, params["before"], limit))
            "most_played" -> {
                val offset = params["offset"]?.toIntOrNull() ?: 0
                call.respond(deps.playHistoryRepository.mostPlayed(sessionId, offset, limit))
            }
            else -> throw ApiException.Validation("sort must be date or most_played")
        }
    }

    put("/api/sessions/{sessionId}/history/paused") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        val body = call.receive<HistoryPausedDto>()
        call.respond(HistoryPausedDto(deps.playHistoryRepository.setPaused(sessionId, body.paused)))
    }

    delete("/api/sessions/{sessionId}/history") {
        val sessionId = requireSessionId(call)
        deps.sessionRepository.requireTvAuth(sessionId, call.bearerToken())
        deps.playHistoryRepository.clear(sessionId)
        call.respond(HttpStatusCode.NoContent)
    }
}
