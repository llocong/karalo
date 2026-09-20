package com.karalo.core.karaoke.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.karalo.core.testing.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Minimal in-memory [DataStore] fake — avoids needing Robolectric/instrumentation just to exercise
 * [TvInstallationIdProviderImpl]'s generate-once/reuse/no-race logic, which doesn't itself depend
 * on real file I/O (that's exactly why the real class takes a plain [DataStore] dependency rather
 * than deriving one internally from a `Context` — see its own doc).
 */
internal class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TvInstallationIdProviderImplTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    @Test
    fun `getOrCreate generates and persists an id on first call`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val dataStore = FakeDataStore()
            val provider = TvInstallationIdProviderImpl(dataStore)

            val id = provider.getOrCreate()

            assertTrue(id.isNotBlank())
            assertEquals(id, dataStore.data.value[TV_INSTALLATION_ID_KEY])
        }

    @Test
    fun `getOrCreate returns the same id on a second call without generating a new one`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val dataStore = FakeDataStore()
            val provider = TvInstallationIdProviderImpl(dataStore)

            val first = provider.getOrCreate()
            val second = provider.getOrCreate()

            assertEquals(first, second)
        }

    @Test
    fun `concurrent first calls do not race to different ids`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val dataStore = FakeDataStore()
            val provider = TvInstallationIdProviderImpl(dataStore)

            val results = (1..20).map { async { provider.getOrCreate() } }.awaitAll()

            assertEquals(1, results.toSet().size, "every concurrent caller must observe the same id")
        }
}
