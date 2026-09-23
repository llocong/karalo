package com.karalo.backend.routes

import com.karalo.backend.AppDependencies
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * Auth happens BEFORE completing the WS upgrade wherever possible so a bad token is a plain HTTP
 * 401 rather than a WS close code — simpler to test and to reason about client-side. Ktor's
 * `webSocket { }` block itself always completes the HTTP Upgrade handshake first (that's how the
 * WebSocket protocol works), so "before the upgrade" here means: validate synchronously as the
 * very first thing inside the block and immediately close with policy-violation if invalid, before
 * ever registering the socket or reading a frame — no partially-registered/partially-trusted state
 * is ever observable to other code.
 */
fun Route.webSocketRoutes(deps: AppDependencies) {
    webSocket("/ws/tv/{sessionId}") {
        val sessionId = call.parameters["sessionId"]
        val secret = call.request.headers["Authorization"]?.removePrefix("Bearer ")
        if (sessionId == null || secret == null || !runCatching { deps.sessionRepository.requireTvAuth(sessionId, secret) }.isSuccess) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
            return@webSocket
        }
        val room = deps.broadcaster.room(sessionId)
        room.setTvSocket(this as DefaultWebSocketServerSession)
        try {
            incoming.consumeAsFlow().collect { /* TV doesn't send anything meaningful today; pings keep it alive. */ }
        } finally {
            room.setTvSocket(null)
        }
    }

    webSocket("/ws/session/{sessionId}") {
        val sessionId = call.parameters["sessionId"]
        val token = call.request.queryParameters["token"]
        if (sessionId == null || token == null || runCatching { deps.participantRepository.requireParticipantAuth(sessionId, token) }.isFailure) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
            return@webSocket
        }
        val room = deps.broadcaster.room(sessionId)
        val session = this as DefaultWebSocketServerSession
        room.phoneSockets.add(session)
        try {
            incoming.consumeAsFlow().collect { /* phones only receive; no client->server WS messages in this MVP. */ }
        } finally {
            room.phoneSockets.remove(session)
        }
    }
}
