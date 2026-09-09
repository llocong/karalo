package com.karalo.feature.player.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun item(id: String) = PlayableItem(id, "Title $id", "Channel", null)

class PlaybackQueueTest {

    @Test
    fun `empty queue has no current item and no navigation`() {
        val queue = PlaybackQueue.empty()

        assertNull(queue.current)
        assertFalse(queue.hasNext)
        assertFalse(queue.hasPrevious)
    }

    @Test
    fun `singleItem queue has no next or previous`() {
        val queue = PlaybackQueue.singleItem(item("a"))

        assertEquals("a", queue.current?.videoId)
        assertFalse(queue.hasNext)
        assertFalse(queue.hasPrevious)
    }

    @Test
    fun `next advances the current index`() {
        val queue = PlaybackQueue(listOf(item("a"), item("b"), item("c")), currentIndex = 0)

        val advanced = queue.next()

        assertEquals("b", advanced.current?.videoId)
        assertTrue(advanced.hasPrevious)
        assertTrue(advanced.hasNext)
    }

    @Test
    fun `next at the last item is a no-op`() {
        val queue = PlaybackQueue(listOf(item("a"), item("b")), currentIndex = 1)

        val advanced = queue.next()

        assertEquals(queue, advanced)
    }

    @Test
    fun `previous rewinds the current index`() {
        val queue = PlaybackQueue(listOf(item("a"), item("b"), item("c")), currentIndex = 2)

        val rewound = queue.previous()

        assertEquals("b", rewound.current?.videoId)
    }

    @Test
    fun `previous at the first item is a no-op`() {
        val queue = PlaybackQueue(listOf(item("a"), item("b")), currentIndex = 0)

        val rewound = queue.previous()

        assertEquals(queue, rewound)
    }
}
