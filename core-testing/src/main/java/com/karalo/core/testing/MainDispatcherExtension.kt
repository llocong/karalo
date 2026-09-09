package com.karalo.core.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit5 extension that swaps `Dispatchers.Main` for a [TestDispatcher] around each test, so
 * ViewModels using viewModelScope (which defaults to Dispatchers.Main) run deterministically.
 *
 * Usage: `@JvmField @RegisterExtension val mainDispatcherExtension = MainDispatcherExtension()`
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        resetMain()
    }
}
