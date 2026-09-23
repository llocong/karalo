package com.karalo.youtubeclient.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchTopUpTest {
    private val isEven: (Int) -> Boolean = { it % 2 == 0 }

    @Test
    fun `fetches one more page when too few results pass`() {
        var fetches = 0

        val result = filterWithOneTopUp(listOf(1, 2, 3), isEven, minResults = 3) { fetches++; listOf(4, 5, 6) }

        assertEquals(listOf(2, 4, 6), result)
        assertEquals(1, fetches)
    }

    @Test
    fun `never fetches more than one extra page, even if results stay few`() {
        var fetches = 0

        val result = filterWithOneTopUp(listOf(1, 3), isEven, minResults = 10) { fetches++; listOf(5, 8) }

        assertEquals(listOf(8), result)
        assertEquals(1, fetches)
    }

    @Test
    fun `does not fetch another page when enough results already pass`() {
        var fetches = 0

        val result = filterWithOneTopUp(listOf(2, 4, 5), isEven, minResults = 2) { fetches++; listOf(6) }

        assertEquals(listOf(2, 4), result)
        assertEquals(0, fetches)
    }

    @Test
    fun `keeps the first page when there is no next page or fetching it fails`() {
        assertEquals(listOf(2), filterWithOneTopUp(listOf(1, 2), isEven, minResults = 10) { null })
        assertEquals(listOf(2), filterWithOneTopUp(listOf(1, 2), isEven, minResults = 10) { error("network") })
    }
}
