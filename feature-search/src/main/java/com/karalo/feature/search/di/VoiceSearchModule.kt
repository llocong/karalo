package com.karalo.feature.search.di

import com.karalo.feature.search.voice.VoiceSearchManager
import com.karalo.feature.search.voice.VoiceSearchManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceSearchModule {
    @Binds
    abstract fun bindVoiceSearchManager(impl: VoiceSearchManagerImpl): VoiceSearchManager
}
