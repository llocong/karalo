package com.karalo.feature.player.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private fun item(id: String) = PlayableItem(id, "Title $id", "Channel", null)

class PlaybackQueueTest {
    @Test
    fun `empty queue has no current item`() {
        val queue = PlaybackQueue.empty()

        assertNull(queue.current)
    }

    @Test
    fun `singleItem queue's current item is that item`() {
        val queue = PlaybackQueue.singleItem(item("a"))

        assertEquals("a", queue.current?.videoId)
    }

    @Test
    fun `current item is the one at currentIndex`() {
        val queue = PlaybackQueue(listOf(item("a"), item("b"), item("c")), currentIndex = 1)

        assertEquals("b", queue.current?.videoId)
    }
}
