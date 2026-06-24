package com.gshashank.btcagent.ui.positions

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.PositionsRepository
import com.gshashank.btcagent.data.repository.PositionsResult
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for [PositionsListViewModel] — MOBILE-6.
 *
 * Uses [FakePositionsRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [UnconfinedTestDispatcher] as [Dispatchers.Main] so that
 * [viewModelScope]-backed coroutines are driven synchronously.
 *
 * All tests MUST fail (red) until [PositionsListViewModel] is implemented.
 *
 * Test coverage:
 *   1. Loading → Ready with summary totals and position list.
 *   2. Empty positions list → UiState.Empty.
 *   3. Fetch failure → UiState.Error.
 *   4. NetworkMonitor offline → UiState.Offline.
 *   5. retry() from Error → Loading → Ready.
 *   6. summary totals = sum of computed unrealized + exposure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakePositionsRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    /** A stable open position used as a success payload. entry=50000, current=51000,
     *  qty=2, contractSize=0.001 → pnl = (51000-50000)*2*0.001 = 2.0,
     *  pnlPct = 2.0 / (50000*2*0.001) * 100 = 2.0%,
     *  exposure = 50000 * 2 * 0.001 = 100.0 */
    private val sampleLongPosition = Position(
        signalId = "sig-001",
        side = Side.Long,
        entryPrice = 50_000.0,
        currentPrice = 51_000.0,
        qty = 2.0,
        sl = 49_000.0,
        tp = 53_000.0,
        status = "open",
        openedAt = "2026-06-24T10:00:00Z",
        pnl = 2.0,
        pnlPct = 2.0,
        contractSize = 0.001,
    )

    /** A stable short position used to test short P&L direction. entry=50000, current=49000,
     *  qty=2, contractSize=0.001 → pnl = (50000-49000)*2*0.001 = 2.0 */
    private val sampleShortPosition = Position(
        signalId = "sig-002",
        side = Side.Short,
        entryPrice = 50_000.0,
        currentPrice = 49_000.0,
        qty = 2.0,
        sl = 51_000.0,
        tp = 47_000.0,
        status = "open",
        openedAt = "2026-06-24T09:00:00Z",
        pnl = 2.0,
        pnlPct = 2.0,
        contractSize = 0.001,
    )

    @Before
    fun setUp() {
        fakeRepo = FakePositionsRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): PositionsListViewModel =
        PositionsListViewModel(
            repository = fakeRepo,
            networkMonitor = mockNetworkMonitor,
        )

    // =========================================================================
    // 1. Loading → Ready with summary totals and position list
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

    @Test
    fun `fetch success emits Ready with positions list`() = runTest {
        fakeRepo.fetchPositionsResult =
            PositionsResult.Success(listOf(sampleLongPosition, sampleShortPosition))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val screenData = (ready as UiState.Ready<PositionsScreenData>).data
            assertEquals(
                "positions list must contain 2 items",
                2,
                screenData.positions.size,
            )
            assertEquals(
                "first position signalId must match",
                "sig-001",
                screenData.positions[0].signalId,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 2. Empty positions list → UiState.Empty
    // =========================================================================

    @Test
    fun `empty positions list emits UiState Empty`() = runTest {
        fakeRepo.fetchPositionsResult = PositionsResult.Success(emptyList())

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val empty = awaitItem()
            assertTrue(
                "When positions list is empty the state must be UiState.Empty, got $empty",
                empty is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Fetch failure → UiState.Error
    // =========================================================================

    @Test
    fun `fetch failure emits UiState Error`() = runTest {
        fakeRepo.fetchPositionsResult = PositionsResult.Error(message = "network unavailable")

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
    // 4. NetworkMonitor offline → UiState.Offline
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits UiState Offline`() = runTest {
        fakeRepo.fetchPositionsResult =
            PositionsResult.Success(listOf(sampleLongPosition))
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
    // 5. retry() from Error → Loading → Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.fetchPositionsResult = PositionsResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.fetchPositionsResult =
                PositionsResult.Success(listOf(sampleLongPosition))

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
    // 6. Summary totals = sum of computed unrealized + exposure
    // =========================================================================

    @Test
    fun `summary unrealizedTotal equals sum of position pnl`() = runTest {
        // sampleLongPosition.pnl = 2.0, sampleShortPosition.pnl = 2.0 → total = 4.0
        fakeRepo.fetchPositionsResult =
            PositionsResult.Success(listOf(sampleLongPosition, sampleShortPosition))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Must be Ready, got $ready", ready is UiState.Ready)
            val screenData = (ready as UiState.Ready<PositionsScreenData>).data

            assertEquals(
                "unrealizedTotal must be sum of all position pnl values",
                4.0,
                screenData.unrealizedTotal,
                0.0001,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `summary exposureTotal equals sum of entry times qty times contractSize`() = runTest {
        // sampleLongPosition exposure = 50000 * 2 * 0.001 = 100.0
        // sampleShortPosition exposure = 50000 * 2 * 0.001 = 100.0 → total = 200.0
        fakeRepo.fetchPositionsResult =
            PositionsResult.Success(listOf(sampleLongPosition, sampleShortPosition))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Must be Ready, got $ready", ready is UiState.Ready)
            val screenData = (ready as UiState.Ready<PositionsScreenData>).data

            assertEquals(
                "exposureTotal must be sum of (entryPrice * qty * contractSize) per position",
                200.0,
                screenData.exposureTotal,
                0.0001,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Configurable fake [PositionsRepository].
 * Set [fetchPositionsResult] before each test to control what [fetchPositions] returns.
 */
private class FakePositionsRepository : PositionsRepository {

    var fetchPositionsResult: PositionsResult =
        PositionsResult.Error(message = "fetchPositionsResult not configured")

    override suspend fun fetchPositions(): PositionsResult = fetchPositionsResult

    override suspend fun close(signalId: String): ActionResult =
        ActionResult.Success

    override suspend fun editTpSl(signalId: String, sl: Double?, tp: Double?): ActionResult =
        ActionResult.Success
}
