package com.karalo.core.common.session

import com.karalo.core.common.model.PlayableItemRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemorySearchSessionHolderTest {
    private val holder = InMemorySearchSessionHolder()

    @Test
    fun `starts empty`() {
        assertTrue(holder.getLastResults().isEmpty())
    }

    @Test
    fun `returns the last results that were set`() {
        val items =
            listOf(
                PlayableItemRef("abc", "Karaoke Song", "Channel", null, 180_000L),
            )

        holder.setLastResults(items)

        assertEquals(items, holder.getLastResults())
    }

    @Test
    fun `a later set replaces the previous results`() {
        holder.setLastResults(listOf(PlayableItemRef("first", "A", "C", null, null)))
        holder.setLastResults(listOf(PlayableItemRef("second", "B", "C", null, null)))

        assertEquals("second", holder.getLastResults().single().videoId)
    }

    @Test
    fun `clear empties the held results`() {
        holder.setLastResults(listOf(PlayableItemRef("abc", "A", "C", null, null)))

        holder.clear()

        assertTrue(holder.getLastResults().isEmpty())
    }
}
