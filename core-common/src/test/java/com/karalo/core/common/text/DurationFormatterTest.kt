package com.karalo.core.common.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DurationFormatterTest {
    @Test
    fun `formats seconds under a minute`() {
        assertEquals("0:05", formatDuration(5))
    }

    @Test
    fun `formats minutes and seconds, zero-padding seconds`() {
        assertEquals("3:07", formatDuration(187))
    }

    @Test
    fun `formats an hour or more as h-mm-ss`() {
        assertEquals("1:02:03", formatDuration(3723))
    }
}
