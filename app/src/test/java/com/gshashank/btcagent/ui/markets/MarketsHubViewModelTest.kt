package com.gshashank.btcagent.ui.markets

import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [MarketsHubViewModel] — MOBILE-10.
 *
 * [MarketsHubViewModel] has an `@Inject constructor()` with no dependencies, so it is
 * constructed directly in tests — no Hilt, no fake collaborators needed.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so that any [viewModelScope]-backed coroutines run
 * synchronously in the test thread.
 *
 * All tests are expected to FAIL (red) until [MarketsHubViewModel] is implemented.
 * No catalog flag is involved in this story (MOBILE-10 explicitly opted out of a flag).
 *
 * Test coverage:
 *   1. ViewModel constructs without error.
 *   2. `uiState` is non-null immediately after construction.
 *   3. `uiState.value` equals the expected initial stub state (MarketsHubUiState.Stub or
 *      equivalent data object / Unit-emitting flow — whatever the implementation exposes).
 *   4. `uiState` continues to hold a non-null, stable value after coroutines are drained.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketsHubViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // -------------------------------------------------------------------------
    // 1. ViewModel constructs without error
    // -------------------------------------------------------------------------

    @Test
    fun `MarketsHubViewModel constructs without error`() {
        // If MarketsHubViewModel does not exist or fails to instantiate, this test fails with
        // a compile error or a runtime exception — either satisfies the "red" requirement.
        val viewModel = MarketsHubViewModel()

        assertNotNull(
            "MarketsHubViewModel instance must be non-null after construction",
            viewModel,
        )
    }

    // -------------------------------------------------------------------------
    // 2. uiState is non-null immediately after construction
    // -------------------------------------------------------------------------

    @Test
    fun `uiState is non-null immediately after construction`() {
        val viewModel = MarketsHubViewModel()

        assertNotNull(
            "uiState StateFlow must be non-null immediately after construction",
            viewModel.uiState,
        )
    }

    // -------------------------------------------------------------------------
    // 3. uiState.value is the initial stub state at construction time
    // -------------------------------------------------------------------------

    @Test
    fun `uiState value equals Stub at construction time`() {
        val viewModel = MarketsHubViewModel()

        // The ViewModel exposes MarketsHubUiState.Stub as its initial value.
        // This assertion will fail to compile until MarketsHubUiState exists with a Stub variant,
        // and will fail at runtime until the ViewModel sets that as the initial value.
        val initialState = viewModel.uiState.value

        assertNotNull(
            "uiState.value must not be null immediately after construction",
            initialState,
        )

        // Pin the exact initial type: must be MarketsHubUiState.Stub (the sentinel value
        // declared for this story — a no-data placeholder the hub can render before MOBILE-24).
        assert(initialState is MarketsHubUiState.Stub) {
            "Expected initial uiState to be MarketsHubUiState.Stub but was $initialState"
        }
    }

    // -------------------------------------------------------------------------
    // 4. uiState remains Stub after coroutines are drained (no background mutation)
    // -------------------------------------------------------------------------

    @Test
    fun `uiState remains Stub after coroutines are drained`() = runTest {
        val viewModel = MarketsHubViewModel()

        // Allow any init{} coroutines to complete.
        advanceUntilIdle()

        val stateAfterDrain = viewModel.uiState.value

        assertNotNull(
            "uiState.value must still be non-null after advanceUntilIdle()",
            stateAfterDrain,
        )

        assert(stateAfterDrain is MarketsHubUiState.Stub) {
            "uiState must remain MarketsHubUiState.Stub after all coroutines finish — " +
                "no background state mutation is expected for the stub ViewModel, got $stateAfterDrain"
        }
    }
}
