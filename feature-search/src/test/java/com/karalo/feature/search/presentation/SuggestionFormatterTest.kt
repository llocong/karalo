package com.karalo.feature.search.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuggestionFormatterTest {
    @Test
    fun `strips a trailing karaoke word`() {
        assertEquals("umbrella", formatSuggestion("umbrella karaoke"))
    }

    @Test
    fun `strips a leading karaoke word`() {
        assertEquals("songs to impress", formatSuggestion("karaoke songs to impress"))
    }

    @Test
    fun `strips a karaoke word in the middle without leaving a double space`() {
        assertEquals("rihanna version songs", formatSuggestion("rihanna karaoke version songs"))
    }

    @Test
    fun `is case-insensitive`() {
        assertEquals("umbrella", formatSuggestion("umbrella Karaoke"))
    }

    @Test
    fun `falls back to the raw suggestion if nothing would be left`() {
        assertEquals("karaoke", formatSuggestion("karaoke"))
    }

    @Test
    fun `leaves a suggestion with no karaoke word unchanged`() {
        assertEquals("umbrella rihanna", formatSuggestion("umbrella rihanna"))
    }
}
