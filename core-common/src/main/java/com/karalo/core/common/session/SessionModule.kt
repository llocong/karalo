package com.karalo.core.common.session

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class SessionModule {
    @Binds
    @ActivityRetainedScoped
    abstract fun bindSearchSessionHolder(impl: InMemorySearchSessionHolder): SearchSessionHolder
}
