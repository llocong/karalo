package com.karalo.youtubeclient.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private data class Candidate(
    val label: String,
    val resolutionP: Int?,
)

class StreamSelectionTest {
    @Test
    fun `returns null for an empty candidate list`() {
        val result = StreamSelection.selectBest(emptyList<Candidate>(), Candidate::resolutionP)

        assertNull(result)
    }

    @Test
    fun `picks the highest resolution at or below the preferred cap`() {
        val candidates =
            listOf(
                Candidate("144p", 144),
                Candidate("480p", 480),
                Candidate("720p", 720),
                Candidate("1080p", 1080),
            )

        val result = StreamSelection.selectBest(candidates, Candidate::resolutionP)

        assertEquals("720p", result?.label)
    }

    @Test
    fun `falls back to the lowest resolution when every candidate exceeds the cap`() {
        val candidates = listOf(Candidate("1440p", 1440), Candidate("2160p", 2160))

        val result = StreamSelection.selectBest(candidates, Candidate::resolutionP)

        assertEquals("1440p", result?.label)
    }

    @Test
    fun `falls back to the first candidate when none report a resolution`() {
        val candidates = listOf(Candidate("unknown-a", null), Candidate("unknown-b", null))

        val result = StreamSelection.selectBest(candidates, Candidate::resolutionP)

        assertEquals("unknown-a", result?.label)
    }

    @Test
    fun `respects a custom preferred cap`() {
        val candidates = listOf(Candidate("240p", 240), Candidate("360p", 360))

        val result = StreamSelection.selectBest(candidates, Candidate::resolutionP, preferredMaxResolutionP = 240)

        assertEquals("240p", result?.label)
    }
}
