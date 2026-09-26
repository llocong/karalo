package com.karalo.backend.domain

import io.ktor.http.HttpStatusCode

/**
 * Typed failures thrown by route handlers/repositories and translated to the standard error
 * envelope by [com.karalo.backend.plugins.installStatusPages] — mirrors the Android app's own
 * AppResult/AppError "typed, no bare exceptions across a boundary" philosophy, adapted to Ktor's
 * exception-based routing idiom instead of a return-type wrapper.
 */
sealed class ApiException(
    val code: String,
    val status: HttpStatusCode,
    message: String,
) : Exception(message) {
    class Validation(message: String) : ApiException("VALIDATION", HttpStatusCode.BadRequest, message)

    /**
     * [code] tells the phone why, when it matters: [SESSION_ENDED] and [GUEST_EXPIRED] send it to
     * the matching "session over" page instead of back to Join.
     */
    class Unauthorized(
        message: String = "Unauthorized",
        code: String = "UNAUTHORIZED",
    ) : ApiException(code, HttpStatusCode.Unauthorized, message)

    class NotFound(message: String = "Not found") : ApiException("NOT_FOUND", HttpStatusCode.NotFound, message)

    class Conflict(message: String) : ApiException("CONFLICT", HttpStatusCode.Conflict, message)

    class UpstreamUnavailable(message: String) : ApiException("UPSTREAM_UNAVAILABLE", HttpStatusCode.BadGateway, message)
}

/** The TV went quiet long enough for its session to end (see TV_INACTIVITY_TIMEOUT). */
const val SESSION_ENDED = "SESSION_ENDED"

/** The guest went too long without a meaningful action (see GUEST_INACTIVITY_TIMEOUT). */
const val GUEST_EXPIRED = "GUEST_EXPIRED"
