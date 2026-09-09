package com.karalo.feature.player.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

private const val USER_AGENT = "Karalo/1.0 (Android TV)"

@Module
@InstallIn(SingletonComponent::class)
object ExoPlayerModule {

    @OptIn(UnstableApi::class)
    @Provides
    fun provideMediaSourceFactory(@ApplicationContext context: Context): MediaSource.Factory {
        val dataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
        return DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    }

    /**
     * A new [ExoPlayer] per injection — it's owned and released by whichever
     * [androidx.lifecycle.ViewModel] injects it (see PlayerViewModel.onCleared), not a singleton.
     */
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        mediaSourceFactory: MediaSource.Factory,
    ): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
}
