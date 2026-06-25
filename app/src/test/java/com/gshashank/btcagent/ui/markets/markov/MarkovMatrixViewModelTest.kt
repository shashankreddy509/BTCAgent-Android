package com.gshashank.btcagent.ui.markets.markov

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.MarkovData
import com.gshashank.btcagent.data.model.Regime
import com.gshashank.btcagent.data.model.StationaryDist
import com.gshashank.btcagent.data.model.TickerRegime
import com.gshashank.btcagent.data.repository.MarkovRepository
import com.gshashank.btcagent.data.repository.MarkovResult
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for [MarkovMatrixViewModel] — MOBILE-13.
 *
 * Uses [FakeMarkovRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * All tests MUST fail (red) until [MarkovMatrixViewModel] is implemented.
 *
 * Test coverage:
 *   1. Initial state is [UiState.Loading].
 *   2. Successful fetch → [UiState.Ready] with correct [MarkovData].
 *   3. Default ticker selection = "BTC-USD" when present in tickers.
 *   4. Default ticker selection = first ticker when "BTC-USD" absent.
 *   5. onSelectTicker("ETH-USD") updates selectedTicker without triggering refetch.
 *   6. Empty tickers → [UiState.Empty].
 *   7. Fetch failure → [UiState.Error].
 *   8. retry() from Error → Loading then Ready.
 *   9. NetworkMonitor offline → [UiState.Offline].
 *   10. NetworkMonitor reconnects → triggers refetch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkovMatrixViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeMarkovRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    // -------------------------------------------------------------------------
    // Sample data
    // -------------------------------------------------------------------------

    private val btcTicker = TickerRegime(
        ticker = "BTC-USD",
        market = "crypto",
        regime = Regime.BULL,
        conviction = 0.82,
        stationary = StationaryDist(bear = 0.15, sideways = 0.25, bull = 0.60),
        accuracy = 0.75,
        gradedCount = 30,
        hasError = false,
    )

    private val ethTicker = TickerRegime(
        ticker = "ETH-USD",
        market = "crypto",
        regime = Regime.SIDEWAYS,
        conviction = 0.55,
        stationary = StationaryDist(bear = 0.30, sideways = 0.45, bull = 0.25),
        accuracy = 0.68,
        gradedCount = 25,
        hasError = false,
    )

    private val solTicker = TickerRegime(
        ticker = "SOL-USD",
        market = "crypto",
        regime = Regime.BEAR,
        conviction = 0.70,
        stationary = StationaryDist(bear = 0.55, sideways = 0.30, bull = 0.15),
        accuracy = 0.72,
        gradedCount = 20,
        hasError = false,
    )

    /** A non-empty [MarkovData] containing BTC-USD as first ticker. */
    private val sampleMarkovData = MarkovData(tickers = listOf(btcTicker, ethTicker, solTicker))

    /** A non-empty [MarkovData] WITHOUT "BTC-USD" — used to test fallback default ticker. */
    private val noBtcMarkovData = MarkovData(tickers = listOf(ethTicker, solTicker))

    /** An "empty" [MarkovData] — no tickers. */
    private val emptyMarkovData = MarkovData(tickers = emptyList())

    @Before
    fun setUp() {
        fakeRepo = FakeMarkovRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): MarkovMatrixViewModel =
        MarkovMatrixViewModel(
            repository = fakeRepo,
            networkMonitor = mockNetworkMonitor,
        )

    // =========================================================================
    // 1. Initial state is Loading
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
    // 2. Successful fetch → UiState.Ready with correct MarkovData
    // =========================================================================

    @Test
    fun `fetch success emits Ready with correct MarkovData`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(sampleMarkovData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready<MarkovData>).data

            assertEquals("Ticker count must match", sampleMarkovData.tickers.size, data.tickers.size)
            assertEquals("First ticker must be BTC-USD", "BTC-USD", data.tickers[0].ticker)
            assertEquals("BTC-USD regime must be BULL", Regime.BULL, data.tickers[0].regime)
            assertEquals(
                "BTC-USD conviction must match",
                0.82,
                data.tickers[0].conviction!!,
                0.001,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Default ticker selection = "BTC-USD" when present in tickers
    // =========================================================================

    @Test
    fun `default selectedTicker is BTC-USD when BTC-USD is present in tickers`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(sampleMarkovData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(
            "selectedTicker must be 'BTC-USD' when present in fetched tickers",
            "BTC-USD",
            viewModel.selectedTicker.value,
        )
    }

    // =========================================================================
    // 4. Default ticker selection = first ticker when "BTC-USD" absent
    // =========================================================================

    @Test
    fun `default selectedTicker is first ticker when BTC-USD is absent`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(noBtcMarkovData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(
            "selectedTicker must fall back to the first ticker when BTC-USD is absent",
            "ETH-USD",
            viewModel.selectedTicker.value,
        )
    }

    // =========================================================================
    // 5. onSelectTicker("ETH-USD") updates selectedTicker without triggering refetch
    // =========================================================================

    @Test
    fun `onSelectTicker updates selectedTicker without triggering an additional fetch`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(sampleMarkovData)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Count how many times fetch was called before the selection.
        val fetchCountBefore = fakeRepo.fetchCallCount

        viewModel.onSelectTicker("ETH-USD")

        advanceUntilIdle()

        assertEquals(
            "selectedTicker must be updated to ETH-USD after onSelectTicker",
            "ETH-USD",
            viewModel.selectedTicker.value,
        )
        assertEquals(
            "onSelectTicker must NOT trigger an additional fetchTickers call",
            fetchCountBefore,
            fakeRepo.fetchCallCount,
        )
    }

    // =========================================================================
    // 6. Empty tickers → UiState.Empty
    // =========================================================================

    @Test
    fun `fetch success with empty tickers emits UiState Empty`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(emptyMarkovData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "When MarkovData.isEmpty is true the state must be UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 7. Fetch failure → UiState.Error
    // =========================================================================

    @Test
    fun `fetch failure emits UiState Error`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Error(message = "network unavailable")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(
                "Fetch failure must produce UiState.Error, got $error",
                error is UiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 8. retry() from Error → Loading then Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.fetchTickersResult = MarkovResult.Success(sampleMarkovData)

            viewModel.retry()

            assertEquals(
                "retry() must re-emit Loading before the new result",
                UiState.Loading,
                awaitItem(),
            )
            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue(
                "retry() must emit Ready after Loading when fetch succeeds, got $ready",
                ready is UiState.Ready,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 9. NetworkMonitor offline → UiState.Offline
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits UiState Offline`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(sampleMarkovData)
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())
            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Must be Ready before going offline, got $ready", ready is UiState.Ready)

            networkOnlineFlow.value = false
            advanceUntilIdle()

            val offline = awaitItem()
            assertTrue(
                "uiState must become Offline when NetworkMonitor emits false, got $offline",
                offline is UiState.Offline,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 10. NetworkMonitor reconnects → triggers refetch → Ready
    // =========================================================================

    @Test
    fun `coming back online from Offline triggers refetch and emits Ready`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(sampleMarkovData)
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue("Must be Ready after first fetch", awaitItem() is UiState.Ready)

            // Go offline.
            networkOnlineFlow.value = false
            advanceUntilIdle()
            assertTrue("Must be Offline when network lost", awaitItem() is UiState.Offline)

            // Come back online.
            networkOnlineFlow.value = true
            advanceUntilIdle()

            // Expect Loading then Ready again.
            val loadingAfterReconnect = awaitItem()
            assertTrue(
                "Must emit Loading when coming back online, got $loadingAfterReconnect",
                loadingAfterReconnect is UiState.Loading,
            )
            advanceUntilIdle()

            val readyAfterReconnect = awaitItem()
            assertTrue(
                "Must emit Ready after refetch on reconnect, got $readyAfterReconnect",
                readyAfterReconnect is UiState.Ready,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // Additional: selectedTicker is null until first successful fetch completes
    // =========================================================================

    @Test
    fun `selectedTicker is null before first successful fetch`() = runTest {
        // The fake is configured to error so Ready is never reached.
        fakeRepo.fetchTickersResult = MarkovResult.Error(message = "no data yet")

        val viewModel = createViewModel()

        // Before any coroutine runs, selectedTicker should have no value yet
        // (null or the un-initialized sentinel — must NOT be a real ticker string).
        assertEquals(
            "selectedTicker must be null before any successful fetch",
            null,
            viewModel.selectedTicker.value,
        )
    }

    // =========================================================================
    // Additional: empty success stamps lastSuccessMs so offline banner is not epoch-zero
    // =========================================================================

    @Test
    fun `empty success stamps lastSuccessMs so offline banner is not epoch zero`() = runTest {
        fakeRepo.fetchTickersResult = MarkovResult.Success(emptyMarkovData)
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue("Empty success expected", awaitItem() is UiState.Empty)

            networkOnlineFlow.value = false
            advanceUntilIdle()

            val offline = awaitItem()
            assertTrue("Must transition to Offline, got $offline", offline is UiState.Offline)
            assertTrue(
                "lastUpdatedMs must be > 0 after an empty success (not epoch 0), got " +
                    "${(offline as UiState.Offline).lastUpdatedMs}",
                offline.lastUpdatedMs > 0L,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Configurable fake [MarkovRepository].
 * Set [fetchTickersResult] before each test to control what [fetchTickers] returns.
 * [fetchCallCount] tracks how many times [fetchTickers] was called (for no-refetch assertion).
 */
private class FakeMarkovRepository : MarkovRepository {

    var fetchTickersResult: MarkovResult =
        MarkovResult.Error(message = "fetchTickersResult not configured")

    var fetchCallCount: Int = 0

    override suspend fun fetchTickers(): MarkovResult {
        fetchCallCount++
        return fetchTickersResult
    }
}
