package com.karalo.karalo.di

import com.karalo.core.common.result.AppResult
import com.karalo.core.testing.FakeYouTubeClient
import com.karalo.youtubeclient.YouTubeClient
import com.karalo.youtubeclient.di.YouTubeClientModule
import com.karalo.youtubeclient.model.YtStreamInfo
import com.karalo.youtubeclient.model.YtSuggestion
import com.karalo.youtubeclient.model.YtVideoSummary
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

// Enough items for a shelf/results row to be scrolled deep into -- see
// TvCarouselFocusRestorationRoundTripTest, which needs to focus an item far outside a freshly-
// composed row's initial window before a real Player round trip.
private const val FILLER_ITEM_COUNT = 49

/**
 * Swaps the real NewPipeExtractor-backed client for a scripted fake in instrumented tests, so the
 * full nav flow (Home -> Search -> Results -> Player) can be exercised without a real network
 * call to YouTube.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [YouTubeClientModule::class])
object FakeYouTubeClientModule {
    @Provides
    @Singleton
    fun provideYouTubeClient(): YouTubeClient =
        FakeYouTubeClient().apply {
            searchResult =
                AppResult.Success(
                    listOf(YtVideoSummary("vid1", "Sample Song", "Test Channel", null, 180L)) +
                        (1..FILLER_ITEM_COUNT).map {
                            YtVideoSummary("filler-vid$it", "Filler Song $it", "Test Channel", null, 180L)
                        },
                )
            suggestionsResult = AppResult.Success(listOf(YtSuggestion("karaoke test song")))
            streamResult =
                AppResult.Success(YtStreamInfo("https://example.com/fake.mp4", "video/mp4", isAdaptive = false))
        }
}
