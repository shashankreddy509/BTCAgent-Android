package com.gshashank.btcagent.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 [TestWatcher] rule that installs an [UnconfinedTestDispatcher] as
 * [Dispatchers.Main] before each test and resets it afterward.
 *
 * Usage in a test class:
 * ```kotlin
 * @get:Rule val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * This replaces the old pattern of passing an injected [CoroutineDispatcher] into
 * ViewModel constructors. After the Item-2 (MOBILE-30) migration, ViewModels use
 * [androidx.lifecycle.viewModelScope] directly, so the [Dispatchers.Main] override
 * is the only seam available for driving coroutines in unit tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {

    val testDispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
