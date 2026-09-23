package com.karalo.backend.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

val appJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

fun Application.installSerialization() {
    install(ContentNegotiation) {
        json(appJson)
    }
}
