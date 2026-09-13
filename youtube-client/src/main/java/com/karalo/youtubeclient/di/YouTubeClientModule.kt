package com.karalo.youtubeclient.di

import com.karalo.youtubeclient.YouTubeClient
import com.karalo.youtubeclient.internal.NewPipeBootstrap
import com.karalo.youtubeclient.internal.NewPipeDownloaderAdapter
import com.karalo.youtubeclient.internal.NewPipeYouTubeClient
import com.karalo.youtubeclient.internal.potoken.WebViewPoTokenProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import javax.inject.Singleton

// Public (not internal): androidTest in :app needs to reference this class for
// @TestInstallIn(replaces = [YouTubeClientModule::class]) — see FakeYouTubeClientModule.
@Module
@InstallIn(SingletonComponent::class)
abstract class YouTubeClientModule {
    @Binds
    @Singleton
    abstract fun bindYouTubeClient(impl: NewPipeYouTubeClient): YouTubeClient

    companion object {
        @Provides
        @Singleton
        fun provideDownloader(
            okHttpClient: OkHttpClient,
            poTokenProvider: WebViewPoTokenProvider,
        ): Downloader =
            NewPipeDownloaderAdapter(okHttpClient).also {
                NewPipeBootstrap.ensureInitialized(it, poTokenProvider)
            }
    }
}
