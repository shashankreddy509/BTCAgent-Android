package com.gshashank.btcagent.ui.markets.analytics

import app.cash.turbine.test
import com.gshashank.btcagent.data.model.AnalyticsData
import com.gshashank.btcagent.data.model.TradeMetrics
import com.gshashank.btcagent.data.repository.AnalyticsRepository
import com.gshashank.btcagent.data.repository.AnalyticsResult
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [AnalyticsViewModel] — MOBILE-17.
 *
 * Uses [FakeAnalyticsRepository] (hand-written fake) so no real network or DataStore is touched.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * All tests MUST fail (red) until [AnalyticsViewModel] is implemented.
 *
 * Test coverage:
 *   a. Initial state is UiState.Loading.
 *   b. Successful fetch with non-empty data → UiState.Ready.
 *   c. Successful fetch with isEmpty==true data → UiState.Empty.
 *   d. Repository returns Error → UiState.Error.
 *   e. retry() after Error → back through Loading → Ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeAnalyticsRepository

    // -------------------------------------------------------------------------
    // Sample data
    // -------------------------------------------------------------------------

    /** Non-empty [TradeMetrics] (count > 0 → not isEmpty). */
    private val sampleMetrics = TradeMetrics(
        count = 2,
        winRatePct = 50.0,
        avgWin = 10.0,
        avgLoss = -5.0,
        expectancy = 2.5,
        grossWin = 10.0,
        grossLoss = 5.0,
        profitFactor = 2.0,
        maxDrawdown = 5.0,
    )

    private val sampleData = AnalyticsData(
        metrics = sampleMetrics,
        byPattern = emptyList(),
        equityCurve = listOf(50.0, 150.0),
    )

    /** Empty [AnalyticsData]: metrics.count == 0 → isEmpty == true. */
    private val emptyMetrics = TradeMetrics(
        count = 0,
        winRatePct = 0.0,
        avgWin = 0.0,
        avgLoss = 0.0,
        expectancy = 0.0,
        grossWin = 0.0,
        grossLoss = 0.0,
        profitFactor = null,
        maxDrawdown = 0.0,
    )

    private val emptyData = AnalyticsData(
        metrics = emptyMetrics,
        byPattern = emptyList(),
        equityCurve = emptyList(),
    )

    @Before
    fun setUp() {
        fakeRepo = FakeAnalyticsRepository()
    }

    private fun createViewModel(): AnalyticsViewModel =
        AnalyticsViewModel(repository = fakeRepo)

    // =========================================================================
    // a. initialState_isLoading
    // =========================================================================

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = createViewModel()

        assertEquals(
            "uiState must be Loading immediately after construction before any coroutine runs",
            UiState.Loading,
            viewModel.uiState.value,
        )
    }

    // =========================================================================
    // b. fetch_success_with_data_emits_ready
    //
    // Fake returns Success with non-empty AnalyticsData → UiState.Ready.
    // =========================================================================

    @Test
    fun `fetch success with non-empty data emits UiState Ready`() = runTest {
        fakeRepo.result = AnalyticsResult.Success(sampleData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue(
                "Second emission must be UiState.Ready when repo returns Success with data, got $ready",
                ready is UiState.Ready,
            )
            val data = (ready as UiState.Ready<AnalyticsData>).data
            assertEquals(
                "Ready data metrics.count must match sample data",
                sampleMetrics.count,
                data.metrics.count,
            )
            assertEquals(
                "Ready data equityCurve size must match",
                sampleData.equityCurve.size,
                data.equityCurve.size,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // c. fetch_success_empty_emits_empty
    //
    // Fake returns Success with isEmpty==true → UiState.Empty.
    // =========================================================================

    @Test
    fun `fetch success with isEmpty data emits UiState Empty`() = runTest {
        fakeRepo.result = AnalyticsResult.Success(emptyData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "When AnalyticsData.isEmpty is true the state must be UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // d. fetch_error_emits_error
    //
    // Fake returns Error → UiState.Error.
    // =========================================================================

    @Test
    fun `fetch error emits UiState Error`() = runTest {
        fakeRepo.result = AnalyticsResult.Error(message = "network unavailable")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(
                "Fetch Error must produce UiState.Error, got $error",
                error is UiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // e. retry_refetches
    //
    // After an Error, calling retry() must go back through Loading → Ready.
    // =========================================================================

    @Test
    fun `retry after error goes back through Loading then emits Ready`() = runTest {
        fakeRepo.result = AnalyticsResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be UiState.Error before retry, got $error", error is UiState.Error)

            // Configure the fake to succeed on retry.
            fakeRepo.result = AnalyticsResult.Success(sampleData)

            viewModel.retry()

            assertEquals(
                "retry() must re-emit UiState.Loading before the new result",
                UiState.Loading,
                awaitItem(),
            )
            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue(
                "retry() must emit UiState.Ready after Loading when fetch succeeds, got $ready",
                ready is UiState.Ready,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [AnalyticsRepository].
 * Set [result] before each test to control what [fetch] returns.
 */
private class FakeAnalyticsRepository : AnalyticsRepository {

    var result: AnalyticsResult = AnalyticsResult.Error(message = "result not configured")

    override suspend fun fetch(): AnalyticsResult = result
}
