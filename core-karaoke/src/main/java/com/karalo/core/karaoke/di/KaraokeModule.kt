package com.karalo.core.karaoke.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.karalo.core.karaoke.data.KaraokeRepositoryImpl
import com.karalo.core.karaoke.data.TvInstallationIdProviderImpl
import com.karalo.core.karaoke.data.remote.KaraokeApi
import com.karalo.core.karaoke.data.remote.KaraokeApiImpl
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.core.karaoke.domain.TvInstallationIdProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes [TvInstallationIdProviderImpl]'s DataStore from any other the app might add later. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KaraokeDataStore

private val Context.karaokeIdentityDataStore by preferencesDataStore(name = "karaoke_tv_identity")

@Module
@InstallIn(SingletonComponent::class)
abstract class KaraokeModule {
    @Binds
    @Singleton
    abstract fun bindKaraokeRepository(impl: KaraokeRepositoryImpl): KaraokeRepository

    @Binds
    @Singleton
    abstract fun bindTvInstallationIdProvider(impl: TvInstallationIdProviderImpl): TvInstallationIdProvider

    @Binds
    abstract fun bindKaraokeApi(impl: KaraokeApiImpl): KaraokeApi

    companion object {
        @Provides
        @Singleton
        @KaraokeDataStore
        fun provideKaraokeDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.karaokeIdentityDataStore
    }
}
