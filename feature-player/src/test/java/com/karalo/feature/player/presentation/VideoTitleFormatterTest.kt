package com.karalo.feature.player.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoTitleFormatterTest {
    @Test
    fun `strips a trailing pipe-delimited suffix`() {
        assertEquals(
            "The Weeknd - Save Your Tears",
            formatVideoTitle("The Weeknd - Save Your Tears | Karaoke Version | Karafun"),
        )
    }

    @Test
    fun `strips a trailing parenthetical suffix`() {
        assertEquals(
            "Ella Langley - Choosin' Texas",
            formatVideoTitle("Ella Langley - Choosin' Texas (Karaoke Version)"),
        )
    }

    @Test
    fun `strips a trailing parenthetical suffix containing a dash`() {
        assertEquals(
            "Umbrella - Rihanna",
            formatVideoTitle("Umbrella - Rihanna (Karaoke Songs With Lyrics - Original Key)"),
        )
    }

    @Test
    fun `leaves a title with no known suffix unchanged`() {
        assertEquals("Artist - Song", formatVideoTitle("Artist - Song"))
    }
}
