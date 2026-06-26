package com.gshashank.btcagent.ui.markets.liquidity

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.HeatTier
import com.gshashank.btcagent.data.model.LiquidityLevel
import com.gshashank.btcagent.data.model.LiquidityMapData
import com.gshashank.btcagent.data.repository.LiquidityRepository
import com.gshashank.btcagent.data.repository.LiquidityResult
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
 * JVM unit tests for [LiquidityMapViewModel] — MOBILE-15.
 *
 * Uses [FakeLiquidityRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * All tests MUST fail (red) until [LiquidityMapViewModel] is implemented.
 *
 * Test coverage:
 *   1. Loading → Ready on success with non-empty data.
 *   2. Forbidden → Error with code == "ACCESS_DENIED".
 *   3. repo Error → UiState.Error with code == "ERR_FETCH".
 *   4. Success with empty data → UiState.Empty.
 *   5. retry() from Error → Loading → Ready.
 *   6. NetworkMonitor offline → UiState.Offline.
 *   7. Offline → back online → triggers refetch → Ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeLiquidityRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    // -------------------------------------------------------------------------
    // Sample data
    // -------------------------------------------------------------------------

    private val sampleLevel = LiquidityLevel(
        price = 43200.5,
        tier = HeatTier.HOT,
        notional = 8_500_000.0,
        timestamp = "2026-06-25 14:30:00 UTC",
    )

    private val sampleData = LiquidityMapData(
        levels = listOf(sampleLevel),
        lastUpdated = "2026-06-25 14:30:00 UTC",
    )

    private val emptyData = LiquidityMapData(
        levels = emptyList(),
        lastUpdated = null,
    )

    @Before
    fun setUp() {
        fakeRepo = FakeLiquidityRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): LiquidityMapViewModel =
        LiquidityMapViewModel(
            repository = fakeRepo,
            networkMonitor = mockNetworkMonitor,
        )

    // =========================================================================
    // 1. Loading → Ready on success with non-empty data
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
    fun `fetch success with non-empty data emits Ready with correct LiquidityMapData`() = runTest {
        fakeRepo.result = LiquidityResult.Success(sampleData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready<LiquidityMapData>).data

            assertEquals("Must have 1 level", 1, data.levels.size)
            assertEquals(
                "Level price must match",
                sampleLevel.price,
                data.levels[0].price,
                0.001,
            )
            assertEquals(
                "Level tier must match",
                sampleLevel.tier,
                data.levels[0].tier,
            )
            assertEquals(
                "Level notional must match",
                sampleLevel.notional,
                data.levels[0].notional,
                0.001,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 2. Forbidden → Error with code == "ACCESS_DENIED"
    // =========================================================================

    @Test
    fun `Forbidden result emits UiState Error with code ACCESS_DENIED`() = runTest {
        fakeRepo.result = LiquidityResult.Forbidden

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(
                "Forbidden must produce UiState.Error, got $error",
                error is UiState.Error,
            )
            val uiError = error as UiState.Error
            assertEquals(
                "Error code must be ACCESS_DENIED for Forbidden result",
                "ACCESS_DENIED",
                uiError.code,
            )
            assertEquals(
                "Error message must be 'Access not approved' for Forbidden result",
                "Access not approved",
                uiError.message,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. repo Error → UiState.Error with code == "ERR_FETCH"
    // =========================================================================

    @Test
    fun `repo Error result emits UiState Error with code ERR_FETCH`() = runTest {
        fakeRepo.result = LiquidityResult.Error(message = "network unavailable")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(
                "Repo Error must produce UiState.Error, got $error",
                error is UiState.Error,
            )
            val uiError = error as UiState.Error
            assertEquals(
                "Error code must be ERR_FETCH for repo Error result",
                "ERR_FETCH",
                uiError.code,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. Success with empty data → UiState.Empty
    // =========================================================================

    @Test
    fun `fetch success with empty data emits UiState Empty`() = runTest {
        fakeRepo.result = LiquidityResult.Success(emptyData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "When LiquidityMapData.isEmpty is true the state must be UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. retry() from Error → Loading → Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.result = LiquidityResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.result = LiquidityResult.Success(sampleData)

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
    // 6. NetworkMonitor offline → UiState.Offline
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits UiState Offline`() = runTest {
        fakeRepo.result = LiquidityResult.Success(sampleData)
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
    // 7. Offline → back online → triggers refetch → Ready
    // =========================================================================

    @Test
    fun `coming back online from Offline triggers refetch and emits Ready`() = runTest {
        fakeRepo.result = LiquidityResult.Success(sampleData)
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
    // Additional: empty success stamps lastSuccessMs so offline banner is not epoch-zero
    // =========================================================================

    @Test
    fun `empty success stamps lastSuccessMs so offline banner is not epoch zero`() = runTest {
        fakeRepo.result = LiquidityResult.Success(emptyData)
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
 * Hand-written fake [LiquidityRepository].
 * Set [result] before each test to control what [fetch] returns.
 */
private class FakeLiquidityRepository : LiquidityRepository {

    var result: LiquidityResult = LiquidityResult.Error(message = "result not configured")

    override suspend fun fetch(): LiquidityResult = result
}
