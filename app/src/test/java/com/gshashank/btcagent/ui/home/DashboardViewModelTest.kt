package com.gshashank.btcagent.ui.home

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.BotMode
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.data.model.PriceDirection
import com.gshashank.btcagent.data.repository.DashboardRepository
import com.gshashank.btcagent.data.repository.DashboardResult
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * JVM unit tests for [DashboardViewModel] — MOBILE-5.
 *
 * Uses [FakeDashboardRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main]. The [DashboardViewModel] constructor also accepts
 * an injected [@IoDispatcher] [CoroutineDispatcher] — we pass [StandardTestDispatcher] so all
 * IO-bound coroutines are under test-scheduler control.
 *
 * All tests MUST fail (red) until [DashboardViewModel] is implemented.
 *
 * Test coverage:
 *   1. Initial state is [UiState.Loading].
 *   2. REST success → [UiState.Ready] with correct [DashboardData].
 *   3. REST failure → [UiState.Error].
 *   4. WS price tick updates [DashboardData.btcPrice] in [UiState.Ready] state.
 *   5. Price direction: tick up from previous → [PriceDirection.Up].
 *   6. Price direction: tick down from previous → [PriceDirection.Down].
 *   7. Price direction: equal tick → [PriceDirection.Flat].
 *   8. [NetworkMonitor] emits offline → [UiState.Offline] with [lastUpdatedMs] populated.
 *   9. [retry] from [UiState.Error] → [UiState.Loading] → [UiState.Ready].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeDashboardRepository

    /**
     * NetworkMonitor mock — [isOnlineFlow] is backed by a [MutableStateFlow] so tests can
     * push online/offline transitions via [networkOnlineFlow].
     */
    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    /** A stable base [DashboardData] used as the success payload in multiple tests. */
    private val successData = DashboardData(
        btcPrice = 67_000.0,
        priceDirection = PriceDirection.Flat,
        todayPnlPts = 12.5,
        openPositionCount = 3,
        openUnrealisedPnl = 150.0,
        botRunning = true,
        botMode = BotMode.Paper,
    )

    @Before
    fun setUp() {
        fakeRepo = FakeDashboardRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): DashboardViewModel =
        DashboardViewModel(
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
    // 2. REST success → Ready with correct DashboardData
    // =========================================================================

    @Test
    fun `REST success emits Ready with correct DashboardData`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Success(successData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready).data
            assertEquals(
                "btcPrice must match the value from DashboardResult.Success",
                successData.btcPrice,
                data.btcPrice,
                0.001,
            )
            assertEquals(
                "todayPnlPts must match the value from DashboardResult.Success",
                successData.todayPnlPts,
                data.todayPnlPts,
                0.001,
            )
            assertEquals(
                "openPositionCount must match",
                successData.openPositionCount,
                data.openPositionCount,
            )
            assertEquals(
                "openUnrealisedPnl must match",
                successData.openUnrealisedPnl,
                data.openUnrealisedPnl,
                0.001,
            )
            assertEquals("botRunning must match", successData.botRunning, data.botRunning)
            assertEquals("botMode must match", successData.botMode, data.botMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. REST failure → Error
    // =========================================================================

    @Test
    fun `REST failure emits Error`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Error(
            cause = RuntimeException("network unavailable"),
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val errorState = awaitItem()
            assertTrue(
                "Second emission must be a UiState.Error variant, got $errorState",
                errorState is UiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. WS price tick updates btcPrice in Ready state
    // =========================================================================

    @Test
    fun `WS price tick updates btcPrice in Ready data`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Success(successData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()

            // Consume the initial Ready emission (from REST fetch + first WS coordination).
            val initial = awaitItem()
            assertTrue("Must be Ready after fetch, got $initial", initial is UiState.Ready)

            // Push a price tick via the fake WS flow.
            val newPrice = 68_500.0f
            fakeRepo.emitPrice(newPrice)
            advanceUntilIdle()

            val updated = awaitItem()
            assertTrue("Must still be Ready after WS tick, got $updated", updated is UiState.Ready)
            assertEquals(
                "btcPrice must reflect the latest WS tick",
                newPrice.toDouble(),
                (updated as UiState.Ready).data.btcPrice,
                0.001,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. Price direction: tick up from previous → Up
    // =========================================================================

    @Test
    fun `price tick higher than previous emits PriceDirection Up`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Success(successData.copy(btcPrice = 67_000.0))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            awaitItem() // initial Ready

            // Emit a price higher than the initial 67000
            fakeRepo.emitPrice(68_000.0f)
            advanceUntilIdle()

            val updated = awaitItem()
            assertTrue("Must be Ready, got $updated", updated is UiState.Ready)
            assertEquals(
                "PriceDirection must be Up when new price > previous price",
                PriceDirection.Up,
                (updated as UiState.Ready).data.priceDirection,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 6. Price direction: tick down from previous → Down
    // =========================================================================

    @Test
    fun `price tick lower than previous emits PriceDirection Down`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Success(successData.copy(btcPrice = 67_000.0))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            awaitItem() // initial Ready

            // Emit a price lower than the initial 67000
            fakeRepo.emitPrice(66_000.0f)
            advanceUntilIdle()

            val updated = awaitItem()
            assertTrue("Must be Ready, got $updated", updated is UiState.Ready)
            assertEquals(
                "PriceDirection must be Down when new price < previous price",
                PriceDirection.Down,
                (updated as UiState.Ready).data.priceDirection,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 7. Price direction: equal tick → Flat
    // =========================================================================

    @Test
    fun `price tick equal to previous emits PriceDirection Flat`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Success(successData.copy(btcPrice = 67_000.0))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            awaitItem() // initial Ready

            // Emit the exact same price as the initial value
            fakeRepo.emitPrice(67_000.0f)
            advanceUntilIdle()

            val updated = awaitItem()
            assertTrue("Must be Ready, got $updated", updated is UiState.Ready)
            assertEquals(
                "PriceDirection must be Flat when new price == previous price",
                PriceDirection.Flat,
                (updated as UiState.Ready).data.priceDirection,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 8. NetworkMonitor offline → Offline with lastUpdatedMs populated
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits Offline state with lastUpdatedMs populated`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Success(successData)
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()

            // Consume the Ready state
            val ready = awaitItem()
            assertTrue("Must be Ready before going offline, got $ready", ready is UiState.Ready)

            // Simulate going offline
            networkOnlineFlow.value = false
            advanceUntilIdle()

            val offline = awaitItem()
            assertTrue(
                "uiState must become Offline when NetworkMonitor emits false, got $offline",
                offline is UiState.Offline,
            )
            val offlineState = offline as UiState.Offline
            assertTrue(
                "lastUpdatedMs must be positive (populated from last successful fetch), " +
                    "got ${offlineState.lastUpdatedMs}",
                offlineState.lastUpdatedMs > 0L,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 9. retry() from Error → Loading → Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.fetchStateResult = DashboardResult.Error(cause = RuntimeException("transient"))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Initial Loading + Error cycle
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            // Fix the fake before triggering retry
            fakeRepo.fetchStateResult = DashboardResult.Success(successData)

            viewModel.retry()

            // retry() must re-emit Loading first
            assertEquals(
                "retry() must re-emit Loading before the new result",
                UiState.Loading,
                awaitItem(),
            )
            advanceUntilIdle()

            // Then emit Ready with the new result
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
 * Configurable fake [DashboardRepository].
 *
 * - Set [fetchStateResult] before each test to control [fetchState] return value.
 * - Call [emitPrice] to push a value into [priceFlow].
 */
private class FakeDashboardRepository : DashboardRepository {

    var fetchStateResult: DashboardResult =
        DashboardResult.Error(IllegalStateException("fetchStateResult not configured"))

    private val priceSharedFlow = MutableSharedFlow<Float>(replay = 0)

    override suspend fun fetchState(): DashboardResult = fetchStateResult

    override fun priceFlow(): Flow<Float> = priceSharedFlow

    /**
     * Push a price tick into the shared flow, simulating a WebSocket message arriving
     * from [PriceWebSocketClient].
     */
    suspend fun emitPrice(price: Float) {
        priceSharedFlow.emit(price)
    }
}
