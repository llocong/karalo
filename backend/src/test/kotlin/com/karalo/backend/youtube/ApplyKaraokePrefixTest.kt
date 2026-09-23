package com.karalo.backend.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the exact edge case that motivated extracting this function: the TV Home screen's
 * "Top Picks" shelf literally searches the bare word "karaoke" (see `feature-home/HomeViewModel.kt`
 * `TOP_PICKS_QUERY`), which must not be double-prefixed into "karaoke karaoke".
 */
class ApplyKaraokePrefixTest {
    @Test
    fun `the bare word karaoke is left unchanged, not double-prefixed`() {
        assertEquals("karaoke", applyKaraokePrefix("karaoke"))
    }

    @Test
    fun `the bare word karaoke is recognized regardless of case or surrounding whitespace`() {
        assertEquals("KARAOKE", applyKaraokePrefix("  KARAOKE  "))
    }

    @Test
    fun `a query already starting with karaoke plus a genre is left unchanged`() {
        assertEquals("karaoke pop", applyKaraokePrefix("karaoke pop"))
        assertEquals("karaoke rock", applyKaraokePrefix("karaoke rock"))
        assertEquals("karaoke r&b", applyKaraokePrefix("karaoke r&b"))
    }

    @Test
    fun `an unprefixed query gets karaoke prepended`() {
        assertEquals("karaoke hello", applyKaraokePrefix("hello"))
    }

    @Test
    fun `a query starting with Karaoke in a different case is left unchanged`() {
        assertEquals("Karaoke Pop", applyKaraokePrefix("Karaoke Pop"))
    }
}
