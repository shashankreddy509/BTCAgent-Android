package com.gshashank.btcagent.ui.markets.oi

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.OiSignal
import com.gshashank.btcagent.data.model.OpenInterestData
import com.gshashank.btcagent.data.repository.OpenInterestRepository
import com.gshashank.btcagent.data.repository.OpenInterestResult
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
 * JVM unit tests for [OpenInterestViewModel] — MOBILE-11.
 *
 * Uses [FakeOpenInterestRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * No catalog flag is required for this feature (PLAN.md: "Catalog flag: NONE").
 *
 * All tests MUST fail (red) until [OpenInterestViewModel] is implemented.
 *
 * Test coverage (mirrors BtcRegimeViewModelTest):
 *   1. Initial state is [UiState.Loading].
 *   2. Success with non-empty data → [UiState.Ready] with correct [OpenInterestData].
 *   3. Success with isEmpty data → [UiState.Empty].
 *   4. Error response → [UiState.Error].
 *   5. retry() from Error → Loading → Ready.
 *   6. [NetworkMonitor] offline → [UiState.Offline].
 *   7. Offline → back online → triggers refetch → [UiState.Ready].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenInterestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeOpenInterestRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    /**
     * A non-empty [OpenInterestData] representing a LONG OI signal with a 5-point sparkline.
     * [OpenInterestData.isEmpty] must be false because oiDelta is non-null.
     */
    private val sampleOiData = OpenInterestData(
        oiDelta = 2500.0,
        signal = OiSignal.LONG,
        largeUp = true,
        largeDown = false,
        upperThresh = 5000.0,
        lowerThresh = -5000.0,
        signalAgeMs = 300_000L, // 5 minutes ago
        sparkline = listOf(100.0, 200.0, 300.0, 400.0, 500.0),
    )

    /**
     * An "empty" [OpenInterestData] — null oiDelta and empty sparkline.
     * [OpenInterestData.isEmpty] must be true.
     */
    private val emptyOiData = OpenInterestData(
        oiDelta = null,
        signal = OiSignal.NONE,
        largeUp = false,
        largeDown = false,
        upperThresh = null,
        lowerThresh = null,
        signalAgeMs = null,
        sparkline = emptyList(),
    )

    @Before
    fun setUp() {
        fakeRepo = FakeOpenInterestRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): OpenInterestViewModel =
        OpenInterestViewModel(
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
    // 2. Success with non-empty data → Ready with correct OpenInterestData
    // =========================================================================

    @Test
    fun `fetch success with non-empty data emits Ready with correct OpenInterestData`() = runTest {
        fakeRepo.fetchOpenInterestResult = OpenInterestResult.Success(sampleOiData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready<OpenInterestData>).data

            assertEquals(
                "oiDelta must match the repository value",
                sampleOiData.oiDelta!!,
                data.oiDelta!!,
                0.001,
            )
            assertEquals(
                "signal must match the repository value",
                sampleOiData.signal,
                data.signal,
            )
            assertTrue("largeUp must be true", data.largeUp)
            assertEquals(
                "sparkline size must match the repository value",
                sampleOiData.sparkline.size,
                data.sparkline.size,
            )
            assertEquals(
                "first sparkline point must match",
                sampleOiData.sparkline.first(),
                data.sparkline.first(),
                0.001,
            )
            assertEquals(
                "last sparkline point must match",
                sampleOiData.sparkline.last(),
                data.sparkline.last(),
                0.001,
            )
            assertEquals(
                "upperThresh must match",
                sampleOiData.upperThresh!!,
                data.upperThresh!!,
                0.001,
            )
            assertEquals(
                "lowerThresh must match",
                sampleOiData.lowerThresh!!,
                data.lowerThresh!!,
                0.001,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Success with isEmpty data → UiState.Empty
    // =========================================================================

    @Test
    fun `fetch success with isEmpty data emits UiState Empty`() = runTest {
        fakeRepo.fetchOpenInterestResult = OpenInterestResult.Success(emptyOiData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "When OpenInterestData.isEmpty is true the state must be UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. Error response → UiState.Error
    // =========================================================================

    @Test
    fun `fetch failure emits UiState Error`() = runTest {
        fakeRepo.fetchOpenInterestResult = OpenInterestResult.Error(message = "network unavailable")

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
    // 5. retry() from Error → Loading → Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.fetchOpenInterestResult = OpenInterestResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.fetchOpenInterestResult = OpenInterestResult.Success(sampleOiData)

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
        fakeRepo.fetchOpenInterestResult = OpenInterestResult.Success(sampleOiData)
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
        fakeRepo.fetchOpenInterestResult = OpenInterestResult.Success(sampleOiData)
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
    // Additional: Empty success stamps lastSuccessMs so offline banner is not epoch-zero
    // =========================================================================

    @Test
    fun `empty success stamps lastSuccessMs so offline banner is not epoch zero`() = runTest {
        fakeRepo.fetchOpenInterestResult = OpenInterestResult.Success(emptyOiData)
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
 * Configurable fake [OpenInterestRepository].
 * Set [fetchOpenInterestResult] before each test to control what [fetchOpenInterest] returns.
 */
private class FakeOpenInterestRepository : OpenInterestRepository {

    var fetchOpenInterestResult: OpenInterestResult =
        OpenInterestResult.Error(message = "fetchOpenInterestResult not configured")

    override suspend fun fetchOpenInterest(): OpenInterestResult = fetchOpenInterestResult
}
