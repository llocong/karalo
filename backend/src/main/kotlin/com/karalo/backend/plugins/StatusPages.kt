package com.karalo.backend.plugins

import com.karalo.backend.domain.ApiException
import com.karalo.backend.domain.model.ErrorBodyDto
import com.karalo.backend.domain.model.ErrorEnvelopeDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

/**
 * Translates [ApiException] into the standard `{ "error": { "code", "message" } }` envelope —
 * mirrors the Android app's own AppResult/AppError "typed, no throwing across a boundary"
 * philosophy, adapted to Ktor's exception-based idiom. Anything NOT an [ApiException] (a genuine
 * bug) is logged with its path and mapped to a generic 500 rather than leaking internals.
 */
fun Application.installStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorEnvelopeDto(ErrorBodyDto(cause.code, cause.message ?: cause.code)))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorEnvelopeDto(ErrorBodyDto("VALIDATION", cause.message ?: "Invalid request")))
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception on ${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorEnvelopeDto(ErrorBodyDto("INTERNAL", "Internal server error")))
        }
    }
}
