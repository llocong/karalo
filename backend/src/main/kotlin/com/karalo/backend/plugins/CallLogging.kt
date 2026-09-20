package com.karalo.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import org.slf4j.event.Level

/**
 * Redacts query strings specifically on WebSocket paths (under "/ws/") so a phone's participant
 * token (unavoidably passed as a query param — browsers can't set custom WebSocket handshake
 * headers) never lands in a log line. See the karaoke ADR's security section for the full
 * rationale/accepted limitation.
 */
fun Application.installCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val path = call.request.path()
            val display = if (path.startsWith("/ws/")) path else call.request.uri
            "${call.response.status()} ${call.request.httpMethod.value} $display"
        }
    }
}
