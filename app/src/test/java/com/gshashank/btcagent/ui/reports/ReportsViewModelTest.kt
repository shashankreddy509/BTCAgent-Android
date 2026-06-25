package com.gshashank.btcagent.ui.reports

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.ClosedTrade
import com.gshashank.btcagent.data.model.ReportsData
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.repository.ReportsRepository
import com.gshashank.btcagent.data.repository.ReportsResult
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
 * JVM unit tests for [ReportsViewModel] — MOBILE-7.
 *
 * Uses [FakeReportsRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * All tests MUST fail (red) until [ReportsViewModel] is implemented.
 *
 * Test coverage:
 *   1. Initial state is [UiState.Loading].
 *   2. Fetch success → [UiState.Ready] with correct stats and trades.
 *   3. Empty history → [UiState.Empty] (no trades at all).
 *   4. Fetch fail → [UiState.Error].
 *   5. Offline → [UiState.Offline] when [NetworkMonitor] emits false.
 *   6. retry() from Error → [UiState.Loading] then [UiState.Ready].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeReportsRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    /** A stable [ClosedTrade] representing a winning long trade. */
    private val winningLongTrade = ClosedTrade(
        closedAt = "2026-06-24T10:00:00Z",
        side = Side.Long,
        entryPrice = 50_000.0,
        exitPrice = 51_000.0,
        pnl = 100.0,
        pattern = "Bull Flag",
    )

    /** A stable [ClosedTrade] representing a losing short trade. */
    private val losingShortTrade = ClosedTrade(
        closedAt = "2026-06-24T09:00:00Z",
        side = Side.Short,
        entryPrice = 60_000.0,
        exitPrice = 61_000.0,
        pnl = -50.0,
        pattern = "Bear Flag",
    )

    /**
     * A [ReportsData] instance with two trades (1 win, 1 loss) and realistic stats.
     * signalsToday = 2, winRatePct = 50.0 (1/2), weekPnl = 50.0 (100 - 50).
     */
    private val sampleReportsData = ReportsData(
        signalsToday = 2,
        winRatePct = 50.0,
        weekPnl = 50.0,
        trades = listOf(winningLongTrade, losingShortTrade),
    )

    @Before
    fun setUp() {
        fakeRepo = FakeReportsRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): ReportsViewModel =
        ReportsViewModel(
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
    // 2. Fetch success → Ready with correct stats and trades
    // =========================================================================

    @Test
    fun `fetch success emits Ready with correct stats and trades`() = runTest {
        fakeRepo.fetchReportsResult = ReportsResult.Success(sampleReportsData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready<ReportsData>).data

            assertEquals(
                "signalsToday must match the value returned by the repository",
                sampleReportsData.signalsToday,
                data.signalsToday,
            )
            assertEquals(
                "winRatePct must match the value returned by the repository",
                sampleReportsData.winRatePct,
                data.winRatePct,
                0.001,
            )
            assertEquals(
                "weekPnl must match the value returned by the repository",
                sampleReportsData.weekPnl,
                data.weekPnl,
                0.001,
            )
            assertEquals(
                "trades list size must match",
                sampleReportsData.trades.size,
                data.trades.size,
            )
            assertEquals(
                "first trade closedAt must match",
                winningLongTrade.closedAt,
                data.trades[0].closedAt,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Empty history → UiState.Empty
    // =========================================================================

    @Test
    fun `empty history emits UiState Empty`() = runTest {
        fakeRepo.fetchReportsResult = ReportsResult.Success(
            ReportsData(
                signalsToday = 0,
                winRatePct = 0.0,
                weekPnl = 0.0,
                trades = emptyList(),
            )
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "When trades list is empty the state must be UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty success still stamps lastSuccessMs so later offline banner is not epoch zero`() =
        runTest {
            // Success with no trades → Empty. Then go offline: the Offline.lastUpdatedMs must
            // reflect the successful fetch time, NOT 0L (epoch 1970), which would render "~56y ago".
            fakeRepo.fetchReportsResult = ReportsResult.Success(
                ReportsData(signalsToday = 0, winRatePct = 0.0, weekPnl = 0.0, trades = emptyList())
            )
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

    // =========================================================================
    // 4. Fetch fail → UiState.Error
    // =========================================================================

    @Test
    fun `fetch failure emits UiState Error`() = runTest {
        fakeRepo.fetchReportsResult = ReportsResult.Error(message = "network unavailable")

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
    // 5. Offline → UiState.Offline when NetworkMonitor emits false
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits UiState Offline`() = runTest {
        fakeRepo.fetchReportsResult = ReportsResult.Success(sampleReportsData)
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
    // 6. retry() from Error → Loading → Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.fetchReportsResult = ReportsResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.fetchReportsResult = ReportsResult.Success(sampleReportsData)

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
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Configurable fake [ReportsRepository].
 * Set [fetchReportsResult] before each test to control what [fetchReports] returns.
 */
private class FakeReportsRepository : ReportsRepository {

    var fetchReportsResult: ReportsResult =
        ReportsResult.Error(message = "fetchReportsResult not configured")

    override suspend fun fetchReports(): ReportsResult = fetchReportsResult
}
