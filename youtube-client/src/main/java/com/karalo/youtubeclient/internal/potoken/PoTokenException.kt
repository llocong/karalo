// Adapted from TeamNewPipe/NewPipe (GPL-3.0), app/src/main/java/org/schabi/newpipe/util/potoken/PoTokenException.kt
package com.karalo.youtubeclient.internal.potoken

internal class PoTokenException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

// Thrown if the WebView provided by the system is broken (e.g. only supports a really old JS
// version, so the BotGuard script fails with a syntax error rather than a normal JS exception).
internal class BadWebViewException(
    message: String,
) : Exception(message)

internal fun buildExceptionForJsError(error: String): Exception =
    if (error.contains("SyntaxError")) {
        BadWebViewException(error)
    } else {
        PoTokenException(error)
    }
