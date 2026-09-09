package com.karalo.youtubeclient.internal

/**
 * Pure "pick the best candidate" policy, kept independent of NewPipeExtractor's stream types so
 * it's trivially unit-testable. Prefers the highest resolution at or below
 * [preferredMaxResolutionP] (smooth playback on TV hardware without wasting bandwidth on 4K for a
 * karaoke video); if every candidate exceeds that cap, falls back to the lowest available.
 */
internal object StreamSelection {
    private const val DEFAULT_PREFERRED_MAX_RESOLUTION_P = 720

    fun <T> selectBest(
        candidates: List<T>,
        resolutionP: (T) -> Int?,
        preferredMaxResolutionP: Int = DEFAULT_PREFERRED_MAX_RESOLUTION_P,
    ): T? {
        val ranked = candidates.mapNotNull { candidate -> resolutionP(candidate)?.let { candidate to it } }
        if (ranked.isEmpty()) return candidates.firstOrNull()

        return ranked
            .filter { (_, resolution) -> resolution <= preferredMaxResolutionP }
            .maxByOrNull { (_, resolution) -> resolution }
            ?.first
            ?: ranked.minByOrNull { (_, resolution) -> resolution }?.first
    }
}
