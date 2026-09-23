package com.karalo.feature.search.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KaraokeResultFilterTest {
    private fun item(
        title: String,
        channelName: String = "Some Channel",
        durationSeconds: Long? = 200,
    ) = SearchResultItem("id", title, channelName, null, durationSeconds)

    private fun keeps(
        item: SearchResultItem,
        rawQuery: String = "machine",
    ) = KaraokeResultFilter.isLikelyKaraoke(item, rawQuery)

    @Test
    fun `a karaoke version of the song is kept`() {
        assertTrue(keeps(item("Machine - Imagine Dragons (Karaoke Version)")))
    }

    @Test
    fun `a title starting with karaoke is kept even when the song is named like a blocked term`() {
        assertTrue(keeps(item("Karaoke Machine - Imagine Dragons")))
    }

    @Test
    fun `a karaoke machine review is dropped`() {
        assertFalse(keeps(item("Best Karaoke Machine 2026 Review")))
    }

    @Test
    fun `karaoke machine videos are dropped even when the user searched for machine`() {
        // Real results for "machine" seen on the emulator.
        assertFalse(keeps(item("7 Best Karaoke Machine for Every Budget", durationSeconds = 512)))
        assertFalse(keeps(item("The Singing Machine Karaoke Machine - Best Home Karaoke", durationSeconds = 61)))
        assertFalse(keeps(item("The best Karaoke machine you can buy")))
        assertFalse(keeps(item("Karaoke Machine. Portable and loud. Perfect for any party!", durationSeconds = 161)))
    }

    @Test
    fun `an official music video with no karaoke marker is dropped`() {
        assertFalse(keeps(item("Imagine Dragons - Machine (Official Music Video)", "ImagineDragonsVEVO")))
    }

    @Test
    fun `a known karaoke channel is kept even with a plain title`() {
        assertTrue(keeps(item("Imagine Dragons - Machine", "Sing King")))
    }

    @Test
    fun `a karaoke word in the channel name is enough`() {
        assertTrue(keeps(item("Machine - Imagine Dragons", "Zoom Karaoke Official")))
    }

    @Test
    fun `shorts, long compilations and unknown durations are dropped`() {
        assertFalse(keeps(item("Machine (Karaoke)", durationSeconds = 45)))
        assertFalse(keeps(item("Karaoke Hits (Karaoke)", durationSeconds = 2 * 60 * 60)))
        assertFalse(keeps(item("Machine (Karaoke)", durationSeconds = null)))
    }

    @Test
    fun `a blocked word is allowed when the user searched for it`() {
        val song = item("Microphone Song (Karaoke Version)")
        assertFalse(keeps(song, rawQuery = "song"))
        assertTrue(keeps(song, rawQuery = "microphone song"))
    }

    @Test
    fun `blocked words only match whole words`() {
        assertTrue(keeps(item("Mickey (Karaoke Version)")))
    }

    @Test
    fun `accents and case are ignored`() {
        assertTrue(keeps(item("Tous les mêmes - Stromae (KARAOKÉ)"), rawQuery = "stromae"))
    }

    @Test
    fun `instrumental and sing-along versions are kept`() {
        assertTrue(keeps(item("Machine - Imagine Dragons (Instrumental with Lyrics)")))
        assertTrue(keeps(item("Machine | Sing-Along")))
    }
}
