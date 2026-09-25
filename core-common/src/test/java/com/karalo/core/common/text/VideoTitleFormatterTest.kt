package com.karalo.core.common.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoTitleFormatterTest {
    @Test
    fun `strips a leading Karaoke prefix`() {
        assertEquals(
            "Je te donne - Jean-Jacques Goldman",
            formatVideoTitle("Karaoké Je te donne - Jean-Jacques Goldman"),
        )
    }

    @Test
    fun `strips a leading Karaoke prefix regardless of accent, case or misspelling`() {
        assertEquals("Song - Artist", formatVideoTitle("karaoke Song - Artist"))
        assertEquals("Song - Artist", formatVideoTitle("KARAOKE Song - Artist"))
        assertEquals("Song - Artist", formatVideoTitle("Karoke Song - Artist"))
        assertEquals("Song - Artist", formatVideoTitle("Karoké Song - Artist"))
    }

    @Test
    fun `strips a leading Karaoke prefix followed by a colon or dash separator`() {
        assertEquals("Song - Artist", formatVideoTitle("Karaoké: Song - Artist"))
        assertEquals("Song - Artist", formatVideoTitle("Karaoké - Song - Artist"))
    }

    @Test
    fun `does not strip a word that merely starts with karaoke`() {
        assertEquals("Karaokefest 2024 - Artist", formatVideoTitle("Karaokefest 2024 - Artist"))
    }

    @Test
    fun `strips both a leading and a trailing karaoke marker`() {
        assertEquals(
            "Song - Artist",
            formatVideoTitle("Karaoké Song - Artist (Karaoke Version)"),
        )
    }

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

    @Test
    fun `keeps the raw title when stripping would leave nothing`() {
        val raw = "KARAOKE | Ai Chung Tình Được Mãi | Tone Nữ"
        assertEquals(raw, formatVideoTitle(raw))
    }
}
