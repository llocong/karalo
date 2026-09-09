package com.karalo.karalo.observability

import com.karalo.core.common.logging.Logger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ObservabilityModule {
    @Binds
    abstract fun bindLogger(impl: CrashlyticsLogger): Logger
}
