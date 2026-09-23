package com.karalo.core.common.error

/**
 * Typed error surface for the app. [Extraction] specifically covers failures coming from the
 * unofficial YouTube extraction layer (`:youtube-client`), which fails differently than a
 * generic network error — e.g. YouTube changed an internal API and parsing broke.
 */
sealed class AppError(
    val cause: Throwable? = null,
) {
    class Network(
        cause: Throwable? = null,
    ) : AppError(cause)

    class Extraction(
        val message: String,
        cause: Throwable? = null,
    ) : AppError(cause)

    data object NotFound : AppError()

    /** A request was rejected as unauthenticated/unauthorized (e.g. an invalid backend token). */
    data object Unauthorized : AppError()

    /** A request conflicted with server-side state (e.g. a stale reorder, or a session already ended). */
    class Conflict(
        val message: String,
    ) : AppError()

    class Unknown(
        cause: Throwable? = null,
    ) : AppError(cause)
}
