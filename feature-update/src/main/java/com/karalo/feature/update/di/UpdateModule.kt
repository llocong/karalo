package com.karalo.feature.update.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.karalo.feature.update.BuildConfig
import com.karalo.feature.update.data.AndroidAppPackage
import com.karalo.feature.update.data.AppPackage
import com.karalo.feature.update.data.UpdateConfig
import com.karalo.feature.update.data.UpdateRepositoryImpl
import com.karalo.feature.update.domain.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateDataStore

private val Context.updateDataStore by preferencesDataStore(name = "karalo_updates")

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository

    @Binds
    abstract fun bindAppPackage(impl: AndroidAppPackage): AppPackage

    companion object {
        @Provides
        @Singleton
        @UpdateDataStore
        fun provideUpdateDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.updateDataStore

        @Provides
        fun provideUpdateConfig(
            @ApplicationContext context: Context,
        ): UpdateConfig =
            UpdateConfig(
                manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
                enabled = BuildConfig.SELF_UPDATE,
                updatesDir = File(context.filesDir, "updates"),
            )
    }
}
