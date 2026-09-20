package com.karalo.core.karaoke.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val PING_INTERVAL_SECONDS = 20L

/** Distinguishes this module's long-lived-connection client from `:core-network`'s shared,
 *  short-request-tuned one -- both provide an (otherwise identically-typed) `OkHttpClient`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KaraokeHttpClient

@Module
@InstallIn(SingletonComponent::class)
object KaraokeNetworkModule {
    @Provides
    @Singleton
    fun provideKaraokeJson(): Json =
        Json {
            ignoreUnknownKeys = true
        }

    /**
     * A dedicated client (not the shared `:core-network` one) with a long read timeout — the
     * WebSocket connection to the karaoke backend is meant to sit open for the app's whole
     * lifetime, unlike the short-lived request/response calls `:core-network`'s client is sized
     * for.
     */
    @Provides
    @Singleton
    @KaraokeHttpClient
    fun provideKaraokeOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
}
