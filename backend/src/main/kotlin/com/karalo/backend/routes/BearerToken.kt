package com.karalo.backend.routes

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/** Extracts the raw token from `Authorization: Bearer <token>`, or null if absent/malformed. */
fun ApplicationCall.bearerToken(): String? {
    val header = request.header(HttpHeaders.Authorization) ?: return null
    return header.removePrefix("Bearer ").takeIf { header.startsWith("Bearer ") }
}
