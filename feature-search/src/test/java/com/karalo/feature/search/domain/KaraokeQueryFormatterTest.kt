package com.karalo.feature.search.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class KaraokeQueryFormatterTest {

    @ParameterizedTest
    @CsvSource(
        "Rihanna, karaoke Rihanna",
        "  Rihanna  , karaoke Rihanna",
        "karaoke Rihanna, karaoke Rihanna",
        "Karaoke Rihanna, Karaoke Rihanna",
        "KARAOKE Rihanna, KARAOKE Rihanna",
    )
    fun `format prefixes the query exactly once, case-insensitively`(input: String, expected: String) {
        assertEquals(expected, KaraokeQueryFormatter.format(input))
    }

    @org.junit.jupiter.api.Test
    fun `format on an empty string still adds the prefix`() {
        assertEquals("karaoke", KaraokeQueryFormatter.format("").trim())
    }
}
