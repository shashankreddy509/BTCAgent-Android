package com.gshashank.btcagent.ui.scanner

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.ScanDirection
import com.gshashank.btcagent.data.model.ScanSignal
import com.gshashank.btcagent.data.model.ScannerData
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessResult
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.ScannerRepository
import com.gshashank.btcagent.data.repository.ScannerResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for [ScannerViewModel] — MOBILE-8.
 *
 * Uses [FakeScannerRepository] and [FakeScanAccessRepository] (hand-written fakes) and a
 * mockito-kotlin mock for [NetworkMonitor] so no real network or Android framework calls
 * are made.
 *
 * [MainDispatcherRule] installs [UnconfinedTestDispatcher] as [Dispatchers.Main] so
 * [viewModelScope]-backed coroutines are driven synchronously.
 *
 * All tests MUST fail (red) until [ScannerViewModel] is implemented.
 *
 * Test coverage:
 *   1.  Initial state is [UiState.Loading].
 *   2.  Fetch success with signals → [UiState.Ready] with correct [ScannerData].
 *   3.  Fetch success with empty results → [UiState.Empty].
 *   4.  Fetch error → [UiState.Error].
 *   5.  Offline transition: [NetworkMonitor.isOnlineFlow] emits false → [UiState.Offline].
 *   6.  retry() from Error → Loading → Ready.
 *   7.  canTrigger is false when AccessRepository returns non-admin.
 *   8.  canTrigger is true when AccessRepository returns admin.
 *   9.  triggerScan() when canTrigger=false → triggerState set to error/refused (defense-in-depth).
 *   10. triggerScan() when canTrigger=true → triggers repo, re-fetches.
 *   11. setFilter(ScanFilter.Bullish) narrows list to only Morning Star signals.
 *   12. setFilter(ScanFilter.Bearish) narrows list to only Evening Star signals.
 *   13. setFilter(ScanFilter.Depo) narrows list to only signals with depoLine != null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeScannerRepository
    private lateinit var fakeAccess: FakeScanAccessRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    // ------- stable domain fixtures -------

    private val morningStarSignal = ScanSignal(
        timeframe = "30m",
        pattern = "Morning Star",
        barsAgo = 2,
        openPrice = 60_000.0,
        depoLine = null,
        direction = ScanDirection.Bullish,
    )

    private val eveningStarSignal = ScanSignal(
        timeframe = "60m",
        pattern = "Evening Star",
        barsAgo = 1,
        openPrice = 62_000.0,
        depoLine = 62_100.0,
        direction = ScanDirection.Bearish,
    )

    private val fourFlagSignal = ScanSignal(
        timeframe = "240m",
        pattern = "4-Flag",
        barsAgo = 3,
        openPrice = 59_500.0,
        depoLine = null,
        direction = ScanDirection.Neutral,
    )

    private val sampleScannerData = ScannerData(
        timestamp = "2026-04-12T03:40:05Z",
        signals = listOf(morningStarSignal, eveningStarSignal, fourFlagSignal),
    )

    @Before
    fun setUp() {
        fakeRepo = FakeScannerRepository()
        fakeAccess = FakeScanAccessRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): ScannerViewModel =
        ScannerViewModel(
            repository = fakeRepo,
            accessRepository = fakeAccess,
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
    // 2. Fetch success with signals → UiState.Ready with correct ScannerData
    // =========================================================================

    @Test
    fun `fetch success with signals emits Ready with correct ScannerData`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready<ScannerData>).data

            assertEquals(
                "timestamp must match the repository response",
                sampleScannerData.timestamp,
                data.timestamp,
            )
            assertEquals(
                "signals list size must match",
                sampleScannerData.signals.size,
                data.signals.size,
            )
            assertEquals(
                "first signal pattern must be Morning Star",
                "Morning Star",
                data.signals[0].pattern,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Fetch success with empty results → UiState.Empty
    // =========================================================================

    @Test
    fun `fetch success with empty results emits UiState Empty`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Success(
            ScannerData(timestamp = "2026-04-12T03:40:05Z", signals = emptyList())
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "Empty signals list must produce UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. Fetch error → UiState.Error
    // =========================================================================

    @Test
    fun `fetch error emits UiState Error`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Error(message = "network unavailable")

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
    // 5. Offline transition: NetworkMonitor emits false → UiState.Offline
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits UiState Offline`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)
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
        fakeRepo.fetchScanResult = ScannerResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

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
    // 7. canTrigger is false when AccessRepository returns non-admin
    // =========================================================================

    @Test
    fun `canTrigger is false when user is not admin`() = runTest {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = false)
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertFalse(
            "canTrigger must be false when the user is not an admin",
            viewModel.canTrigger.value,
        )
    }

    // =========================================================================
    // 8. canTrigger is true when AccessRepository returns admin
    // =========================================================================

    @Test
    fun `canTrigger is true when user is admin`() = runTest {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = true)
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertTrue(
            "canTrigger must be true when the user is an admin",
            viewModel.canTrigger.value,
        )
    }

    // =========================================================================
    // 9. triggerScan() when canTrigger=false → triggerState set to error/refused
    // =========================================================================

    @Test
    fun `triggerScan when canTrigger false sets triggerState to error`() = runTest {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = false)
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.triggerState.test {
            awaitItem() // consume initial null state

            viewModel.triggerScan()
            advanceUntilIdle()

            val triggerResult = awaitItem()
            assertNotNull(
                "triggerState must be non-null after triggerScan() called when not admin",
                triggerResult,
            )
            assertTrue(
                "triggerState must be Error when canTrigger is false (local defense-in-depth), got $triggerResult",
                triggerResult is ActionResultUiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 10. triggerScan() when canTrigger=true → triggers repo, re-fetches
    // =========================================================================

    @Test
    fun `triggerScan when canTrigger true calls repo and re-fetches scan data`() = runTest {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = true)
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)
        fakeRepo.triggerScanResult = ActionResult.Success

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.triggerState.test {
            awaitItem() // consume initial null state

            viewModel.triggerScan()
            advanceUntilIdle()

            val triggerResult = awaitItem()
            assertTrue(
                "triggerState must be Success after triggerScan() succeeds, got $triggerResult",
                triggerResult is ActionResultUiState.Success,
            )

            // Repository's triggerScan must have been called
            assertTrue(
                "triggerScan must have been called on the repository at least once",
                fakeRepo.triggerScanCallCount >= 1,
            )
            // And fetchScan must have been called again (re-fetch after trigger)
            assertTrue(
                "fetchScan must be called again after a successful triggerScan (total >= 2)",
                fakeRepo.fetchScanCallCount >= 2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 11. setFilter(ScanFilter.Bullish) narrows list to only Morning Star signals
    // =========================================================================

    @Test
    fun `setFilter Bullish narrows displayed signals to Morning Star only`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.setFilter(ScanFilter.Bullish)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be Ready after setting Bullish filter, got $state", state is UiState.Ready)
        val signals = (state as UiState.Ready<ScannerData>).data.signals

        assertTrue(
            "Bullish filter must include Morning Star signals (direction=Bullish)",
            signals.any { it.pattern == "Morning Star" },
        )
        assertFalse(
            "Bullish filter must exclude Evening Star signals (direction=Bearish)",
            signals.any { it.pattern == "Evening Star" },
        )
        assertFalse(
            "Bullish filter must exclude 4-Flag signals (direction=Neutral)",
            signals.any { it.pattern == "4-Flag" },
        )
    }

    // =========================================================================
    // 12. setFilter(ScanFilter.Bearish) narrows list to only Evening Star signals
    // =========================================================================

    @Test
    fun `setFilter Bearish narrows displayed signals to Evening Star only`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.setFilter(ScanFilter.Bearish)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be Ready after setting Bearish filter, got $state", state is UiState.Ready)
        val signals = (state as UiState.Ready<ScannerData>).data.signals

        assertTrue(
            "Bearish filter must include Evening Star signals (direction=Bearish)",
            signals.any { it.pattern == "Evening Star" },
        )
        assertFalse(
            "Bearish filter must exclude Morning Star signals (direction=Bullish)",
            signals.any { it.pattern == "Morning Star" },
        )
        assertFalse(
            "Bearish filter must exclude 4-Flag signals (direction=Neutral)",
            signals.any { it.pattern == "4-Flag" },
        )
    }

    // =========================================================================
    // 13. setFilter(ScanFilter.Depo) narrows list to only signals with depoLine != null
    // =========================================================================

    @Test
    fun `setFilter Depo narrows displayed signals to those with non-null depoLine`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.setFilter(ScanFilter.Depo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be Ready after setting Depo filter, got $state", state is UiState.Ready)
        val signals = (state as UiState.Ready<ScannerData>).data.signals

        assertTrue(
            "Depo filter must only include signals that have a non-null depoLine",
            signals.all { it.depoLine != null },
        )
        assertTrue(
            "Depo filter must include the Evening Star signal (which has depoLine=62100.0)",
            signals.any { it.pattern == "Evening Star" },
        )
        assertFalse(
            "Depo filter must exclude Morning Star signal (depoLine is null)",
            signals.any { it.pattern == "Morning Star" },
        )
        assertFalse(
            "Depo filter must exclude 4-Flag signal (depoLine is null)",
            signals.any { it.pattern == "4-Flag" },
        )
    }

    // =========================================================================
    // Additional: setFilter(ScanFilter.All) restores full list
    // =========================================================================

    @Test
    fun `setFilter All after Bullish restores full signals list`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.setFilter(ScanFilter.Bullish)
        advanceUntilIdle()

        viewModel.setFilter(ScanFilter.All)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be Ready after resetting to All filter, got $state", state is UiState.Ready)
        val signals = (state as UiState.Ready<ScannerData>).data.signals

        assertEquals(
            "All filter must restore the full signals list (3 signals in sampleScannerData)",
            sampleScannerData.signals.size,
            signals.size,
        )
    }

    // =========================================================================
    // 14. triggerScan() when repo returns ActionResult.Error → triggerState Error
    // =========================================================================

    @Test
    fun `triggerScan maps repository ActionResult Error to triggerState Error`() = runTest {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = true)
        fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)
        fakeRepo.triggerScanResult = ActionResult.Error(code = 500, message = "backend error")

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.triggerState.test {
            awaitItem() // consume initial null state

            viewModel.triggerScan()
            advanceUntilIdle()

            val triggerResult = awaitItem()
            assertTrue(
                "triggerState must be Error when the repository trigger fails, got $triggerResult",
                triggerResult is ActionResultUiState.Error,
            )
            assertEquals(
                "Error code must be propagated from the repository ActionResult.Error",
                500,
                (triggerResult as ActionResultUiState.Error).code,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 15. Online recovery: coming back online from Error re-fetches → Ready
    // =========================================================================

    @Test
    fun `coming back online from Error state re-fetches and emits Ready`() = runTest {
        fakeRepo.fetchScanResult = ScannerResult.Error(message = "transient error")
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before reconnect, got $error", error is UiState.Error)

            // Backend recovers; toggling offline→online must drive a re-fetch from the Error state.
            fakeRepo.fetchScanResult = ScannerResult.Success(sampleScannerData)
            networkOnlineFlow.value = false
            advanceUntilIdle()
            awaitItem() // Offline
            networkOnlineFlow.value = true
            advanceUntilIdle()

            // Drain to a terminal Ready — reconnect path runs doFetch() (Loading → Ready).
            var sawReady = false
            while (!sawReady) {
                val s = awaitItem()
                if (s is UiState.Ready) sawReady = true
            }
            assertTrue("Reconnect from Error must eventually reach Ready", sawReady)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Configurable fake [ScannerRepository].
 * Set [fetchScanResult] and [triggerScanResult] before each test to control results.
 */
private class FakeScannerRepository : ScannerRepository {

    var fetchScanResult: ScannerResult =
        ScannerResult.Error(message = "fetchScanResult not configured")

    var triggerScanResult: ActionResult = ActionResult.Success

    var fetchScanCallCount: Int = 0
    var triggerScanCallCount: Int = 0

    override suspend fun fetchScan(): ScannerResult {
        fetchScanCallCount++
        return fetchScanResult
    }

    override suspend fun triggerScan(): ActionResult {
        triggerScanCallCount++
        return triggerScanResult
    }
}

/**
 * Configurable fake [AccessRepository] for scanner tests.
 */
private class FakeScanAccessRepository : AccessRepository {

    var checkAccessResult: AccessResult =
        AccessResult.Error(cause = IllegalStateException("checkAccessResult not configured"))

    override suspend fun checkAccess(): AccessResult = checkAccessResult
}
