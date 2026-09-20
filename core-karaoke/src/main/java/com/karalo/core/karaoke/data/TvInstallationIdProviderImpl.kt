package com.karalo.core.karaoke.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karalo.core.karaoke.di.KaraokeDataStore
import com.karalo.core.karaoke.domain.TvInstallationIdProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal val TV_INSTALLATION_ID_KEY = stringPreferencesKey("tv_installation_id")

/**
 * A self-generated UUID rather than e.g. `ANDROID_ID`: `ANDROID_ID` can change on factory reset,
 * per-app-per-signing-key, on some OEM TV builds — a self-generated, DataStore-persisted UUID is
 * fully under this app's own control and matches "generate once, reuse forever" regardless of
 * platform ID quirks. DataStore Preferences (not Room/SharedPreferences) since this is exactly one
 * async-native value and the app is otherwise all-coroutines — see the karaoke ADR.
 *
 * Takes [DataStore] as a constructor dependency (provided by [com.karalo.core.karaoke.di.KaraokeModule]
 * from a `Context`) rather than deriving it internally from an injected `Context` -- this is what
 * lets a unit test exercise this class's actual generate-once/reuse/no-race logic against a plain
 * in-memory fake, with no Robolectric/instrumentation needed.
 *
 * Honest limitation, not papered over: if the app is uninstalled or its data is cleared, this ID
 * is lost like any other app-private storage — Android provides no way around that. The backend
 * will then legitimately treat the next launch as a brand-new TV installation.
 */
@Singleton
class TvInstallationIdProviderImpl
    @Inject
    constructor(
        @KaraokeDataStore private val dataStore: DataStore<Preferences>,
    ) : TvInstallationIdProvider {
        private val mutex = Mutex()

        override suspend fun getOrCreate(): String =
            mutex.withLock {
                val existing = dataStore.data.first()[TV_INSTALLATION_ID_KEY]
                if (existing != null) return existing

                val generated = UUID.randomUUID().toString()
                dataStore.edit { prefs -> prefs[TV_INSTALLATION_ID_KEY] = generated }
                generated
            }
    }
