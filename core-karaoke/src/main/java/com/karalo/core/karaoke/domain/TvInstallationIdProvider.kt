package com.karalo.core.karaoke.domain

/** A stable identifier for this TV installation — generated once, persisted, reused forever. */
interface TvInstallationIdProvider {
    suspend fun getOrCreate(): String
}
