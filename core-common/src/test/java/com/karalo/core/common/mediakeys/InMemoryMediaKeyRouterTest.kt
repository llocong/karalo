package com.karalo.core.common.mediakeys

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryMediaKeyRouterTest {
    private val router = InMemoryMediaKeyRouter()

    @Test
    fun `dispatch with no attached handler is not consumed`() {
        assertFalse(router.dispatch(KEY_PLAY_PAUSE))
    }

    @Test
    fun `dispatch forwards to the attached handler`() {
        var receivedKeyCode: Int? = null
        router.attach(
            MediaKeyHandler { keyCode ->
                receivedKeyCode = keyCode
                true
            },
        )

        val consumed = router.dispatch(KEY_PLAY_PAUSE)

        assertTrue(consumed)
        assertTrue(receivedKeyCode == KEY_PLAY_PAUSE)
    }

    @Test
    fun `detaching a handler stops it from receiving events`() {
        val handler = MediaKeyHandler { true }
        router.attach(handler)

        router.detach(handler)

        assertFalse(router.dispatch(KEY_PLAY_PAUSE))
    }

    @Test
    fun `detaching a handler that is not the current one is a no-op`() {
        val first = MediaKeyHandler { true }
        val second = MediaKeyHandler { true }
        router.attach(first)

        router.detach(second)

        assertTrue(router.dispatch(KEY_PLAY_PAUSE))
    }

    private companion object {
        const val KEY_PLAY_PAUSE = 85
    }
}
