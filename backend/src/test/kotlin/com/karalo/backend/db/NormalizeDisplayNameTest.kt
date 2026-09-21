package com.karalo.backend.db

import com.karalo.backend.domain.ApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NormalizeDisplayNameTest {
    @Test
    fun `strips control characters and trims whitespace`() {
        assertEquals("Tom", normalizeDisplayName("  Tom  ", 16))
    }

    @Test
    fun `a name within the limit is left unchanged`() {
        assertEquals("Sixteen Chars!!!", normalizeDisplayName("Sixteen Chars!!!", 16))
    }

    @Test
    fun `a name over the limit is rejected`() {
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName("This name is way too long", 16) }
    }

    @Test
    fun `a blank name is rejected`() {
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName("   ", 16) }
    }

    @Test
    fun `the same input is accepted under a looser limit and rejected under a tighter one`() {
        val name = "Seventeen Chars!!"
        assertEquals(name, normalizeDisplayName(name, 40))
        assertThrows(ApiException.Validation::class.java) { normalizeDisplayName(name, 16) }
    }
}
