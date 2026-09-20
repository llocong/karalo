package com.karalo.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds

fun Application.installSockets() {
    install(WebSockets) {
        pingPeriod = 20.seconds
        timeout = 30.seconds
    }
}
