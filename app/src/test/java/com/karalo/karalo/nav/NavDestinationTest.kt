package com.karalo.karalo.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavDestinationTest {

    @Test
    fun `player route template declares both nav args`() {
        assertEquals("player/{startIndex}?startVideoId={startVideoId}", NavDestination.Player.route)
    }

    @Test
    fun `createRoute fills in the concrete start index and video id`() {
        val route = NavDestination.Player.createRoute(startIndex = 2, startVideoId = "abc123")

        assertEquals("player/2?startVideoId=abc123", route)
    }
}
