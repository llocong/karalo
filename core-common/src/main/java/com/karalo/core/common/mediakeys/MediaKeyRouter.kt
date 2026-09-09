package com.karalo.core.common.mediakeys

/**
 * Routes hardware/remote media key events (KEYCODE_MEDIA_PLAY_PAUSE, _NEXT, _PREVIOUS, ...) from
 * `MainActivity.dispatchKeyEvent` (in `:app`) to whichever player is currently on screen
 * (`:feature-player`'s ViewModel), without `:app` depending on `:feature-player`'s internals.
 */
fun interface MediaKeyHandler {
    /** Returns true if the key event was consumed. */
    fun onMediaKeyEvent(keyCode: Int): Boolean
}

interface MediaKeyRouter {
    fun attach(handler: MediaKeyHandler)

    fun detach(handler: MediaKeyHandler)

    fun dispatch(keyCode: Int): Boolean
}
