package com.karalo.core.common.mediakeys

import javax.inject.Inject
import javax.inject.Singleton

/** The most recently attached handler wins — there's only ever one player screen visible. */
@Singleton
class InMemoryMediaKeyRouter @Inject constructor() : MediaKeyRouter {
    @Volatile
    private var current: MediaKeyHandler? = null

    override fun attach(handler: MediaKeyHandler) {
        current = handler
    }

    override fun detach(handler: MediaKeyHandler) {
        if (current === handler) current = null
    }

    override fun dispatch(keyCode: Int): Boolean = current?.onMediaKeyEvent(keyCode) ?: false
}
