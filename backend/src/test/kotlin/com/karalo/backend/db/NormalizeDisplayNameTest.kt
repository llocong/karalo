package com.karalo.backend.db

import com.karalo.backend.domain.ApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NormalizeDisplayNameTest {
    @Test
    fun `strips control characters and trims whitespace`() {
        assertEquals("Tom", normalizeDisplayName("  To\u0007m\n  "))
    }

    @Test
    fun `a name exactly at the limit is left unchanged`() {
        assertEquals("Sixteen Chars!!!", normalizeDisplayName("Sixteen Chars!!!"))
    }

    @Test
    fun `a name over the limit is rejected`() {
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName("Seventeen Chars!!") }
    }

    @Test
    fun `a blank name is rejected`() {
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName("   ") }
    }

    @Test
    fun `a name made only of invisible characters is rejected`() {
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName("​​﻿") }
    }

    @Test
    fun `zero-width and bidirectional override characters are stripped`() {
        // U+202E would otherwise render everything after it reversed ("Bob" + RLO + "evil").
        assertEquals("Bobevil", normalizeDisplayName("Bob‮evil​"))
    }

    @Test
    fun `the zero-width joiner inside emoji sequences is kept`() {
        val family = "👩‍👧"
        assertEquals("Mia $family", normalizeDisplayName("Mia $family"))
    }

    @Test
    fun `runs of whitespace collapse to a single space`() {
        assertEquals("Ann Lee", normalizeDisplayName("Ann \t   Lee"))
    }

    @Test
    fun `decomposed accents are normalized so they count as one character`() {
        // "e" + combining acute accent, 2 UTF-16 units, becomes the single precomposed "é".
        assertEquals("André", normalizeDisplayName("André"))
    }

    @Test
    fun `angle brackets are rejected`() {
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName("<b>Bob</b>") }
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName("Bob >_<") }
    }

    @Test
    fun `quotes and SQL-looking text are stored as plain text, not rejected`() {
        // Exposed only sends parameterized statements, so this is just an odd name, not an attack.
        assertEquals("Bob'; DROP --", normalizeDisplayName("Bob'; DROP --"))
    }
}
