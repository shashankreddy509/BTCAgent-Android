package com.gshashank.btcagent.ui.trade

import app.cash.turbine.test
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.model.TradingControlData
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.TradingControlRepository
import com.gshashank.btcagent.data.repository.TradingControlResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [TradingControlViewModel] — MOBILE-18.
 *
 * Uses [FakeTradingControlRepository] (hand-written fake) so no real network or Android
 * framework calls are made.
 *
 * [MainDispatcherRule] installs [UnconfinedTestDispatcher] as [Dispatchers.Main] so that
 * [viewModelScope]-backed coroutines are driven synchronously.
 *
 * All tests MUST fail (red) until [TradingControlViewModel] is implemented.
 *
 * Test coverage:
 *   1.  Initial state is UiState.Loading.
 *   2.  fetchState() success → UiState.Ready with correct TradingControlData.
 *   3.  fetchState() error → UiState.Error.
 *   4.  start() success → emits ActionResultUiState.Success AND refreshes uiState.
 *   5.  start() while already in-flight → second call is no-op (double-tap guard).
 *   6.  stop() success → emits ActionResultUiState.Success AND refreshes uiState.
 *   7.  setMode(ExecutionMode.LIVE) only fires write AFTER confirmLiveMode() is called.
 *   8.  setMode(ExecutionMode.LIVE) then cancelLiveMode() → no write fired.
 *   9.  setMode(ExecutionMode.PAPER) → fires write immediately (no confirm needed).
 *   10. setDepoAlerts(true) success → refreshes uiState.
 *   11. setDepoAlerts(false) success → refreshes uiState.
 *   12. close(signalId) success → emits ActionResultUiState.Success AND refreshes uiState.
 *   13. error result → ActionResultUiState.Error with message.
 *   14. actionResult resets to null after clearActionResult() is called.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradingControlViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeTradingControlRepository

    // -------------------------------------------------------------------------
    // Stable domain fixtures
    // -------------------------------------------------------------------------

    private val samplePosition = Position(
        signalId = "sig-001",
        side = Side.Long,
        entryPrice = 50_000.0,
        currentPrice = 51_000.0,
        qty = 1.0,
        sl = 49_000.0,
        tp = 53_000.0,
        status = "open",
        openedAt = "2026-06-25T10:00:00Z",
        pnl = 1.0,
        pnlPct = 1.0,
        contractSize = 0.001,
    )

    private val sampleData = TradingControlData(
        running = true,
        mode = ExecutionMode.LIVE,
        depoAlertsEnabled = true,
        positions = listOf(samplePosition),
    )

    private val sampleDataPaper = TradingControlData(
        running = false,
        mode = ExecutionMode.PAPER,
        depoAlertsEnabled = false,
        positions = emptyList(),
    )

    @Before
    fun setUp() {
        fakeRepo = FakeTradingControlRepository()
    }

    private fun createViewModel(): TradingControlViewModel =
        TradingControlViewModel(repository = fakeRepo)

    // =========================================================================
    // 1. Initial state is UiState.Loading
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
    // 2. fetchState() success → UiState.Ready with correct TradingControlData
    // =========================================================================

    @Test
    fun `fetchState success emits UiState Ready with correct TradingControlData`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue(
                "Second emission must be UiState.Ready when repo returns Success, got $ready",
                ready is UiState.Ready,
            )
            val data = (ready as UiState.Ready<TradingControlData>).data

            assertTrue("running must be true", data.running)
            assertEquals(
                "mode must be ExecutionMode.LIVE",
                ExecutionMode.LIVE,
                data.mode,
            )
            assertTrue("depoAlertsEnabled must be true", data.depoAlertsEnabled)
            assertEquals(
                "positions size must match",
                1,
                data.positions.size,
            )
            assertEquals(
                "first position signalId must match",
                "sig-001",
                data.positions[0].signalId,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. fetchState() error → UiState.Error
    // =========================================================================

    @Test
    fun `fetchState error emits UiState Error`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Error(message = "network unavailable")

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
    // 4. start() success → emits ActionResultUiState.Success AND refreshes uiState
    // =========================================================================

    @Test
    fun `start success emits ActionResultUiState Success and refreshes uiState`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)
        fakeRepo.startResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle() // allow initial fetch to complete

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.start()
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be ActionResultUiState.Success after start() succeeds, got $result",
                result is ActionResultUiState.Success,
            )

            // Refresh: fetchState must have been called more than once
            assertTrue(
                "fetchState must be called again after start() succeeds (at least 2 total calls)",
                fakeRepo.fetchStateCallCount >= 2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. start() while already in-flight → second call is no-op (double-tap guard)
    // =========================================================================

    @Test
    fun `second start call while first is in-flight is ignored (double-tap guard)`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)
        fakeRepo.startResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val startCallsBefore = fakeRepo.startCallCount

        // Fire two consecutive calls without letting them complete between calls
        viewModel.start()
        viewModel.start()
        advanceUntilIdle()

        assertEquals(
            "Double-tap guard: start() must only be forwarded to the repo once, not twice",
            startCallsBefore + 1,
            fakeRepo.startCallCount,
        )
    }

    // =========================================================================
    // 6. stop() success → emits ActionResultUiState.Success AND refreshes uiState
    // =========================================================================

    @Test
    fun `stop success emits ActionResultUiState Success and refreshes uiState`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)
        fakeRepo.stopResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.stop()
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be ActionResultUiState.Success after stop() succeeds, got $result",
                result is ActionResultUiState.Success,
            )
            assertTrue(
                "fetchState must be called again after stop() succeeds",
                fakeRepo.fetchStateCallCount >= 2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 7. setMode(ExecutionMode.LIVE) only fires write AFTER confirmLiveMode() is called
    //    (confirm intent accepted)
    // =========================================================================

    @Test
    fun `setMode LIVE only fires write after confirmLiveMode is called`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleDataPaper)
        fakeRepo.setModeResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val setModeCallsBefore = fakeRepo.setModeCallCount

        // First call: signals intent to switch to LIVE — must NOT fire the write yet
        viewModel.setMode(ExecutionMode.LIVE)
        advanceUntilIdle()

        assertEquals(
            "setMode(LIVE) must NOT call the repo write before the confirm dialog is accepted",
            setModeCallsBefore,
            fakeRepo.setModeCallCount,
        )

        // Accept the confirm dialog — now the write must fire
        viewModel.confirmLiveMode()
        advanceUntilIdle()

        assertEquals(
            "After confirmLiveMode(), the repo write must be called exactly once",
            setModeCallsBefore + 1,
            fakeRepo.setModeCallCount,
        )
    }

    // =========================================================================
    // 8. setMode(ExecutionMode.LIVE) then cancelLiveMode() → no write fired
    // =========================================================================

    @Test
    fun `setMode LIVE then cancelLiveMode causes no write to fire`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleDataPaper)
        fakeRepo.setModeResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val setModeCallsBefore = fakeRepo.setModeCallCount

        viewModel.setMode(ExecutionMode.LIVE)
        advanceUntilIdle()

        // Dismiss/cancel the confirm dialog
        viewModel.cancelLiveMode()
        advanceUntilIdle()

        assertEquals(
            "cancelLiveMode() must prevent any write to the repo — no setMode call expected",
            setModeCallsBefore,
            fakeRepo.setModeCallCount,
        )
    }

    // =========================================================================
    // 9. setMode(ExecutionMode.PAPER) → fires write immediately (no confirm needed)
    // =========================================================================

    @Test
    fun `setMode PAPER fires write immediately without confirm`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData) // currently LIVE
        fakeRepo.setModeResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val setModeCallsBefore = fakeRepo.setModeCallCount

        viewModel.setMode(ExecutionMode.PAPER)
        advanceUntilIdle()

        assertEquals(
            "setMode(PAPER) must fire the repo write immediately (no confirm dialog needed)",
            setModeCallsBefore + 1,
            fakeRepo.setModeCallCount,
        )
    }

    // =========================================================================
    // 10. setDepoAlerts(true) success → refreshes uiState
    // =========================================================================

    @Test
    fun `setDepoAlerts true success refreshes uiState`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleDataPaper)
        fakeRepo.setDepoAlertsResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val fetchCountBefore = fakeRepo.fetchStateCallCount

        viewModel.setDepoAlerts(true)
        advanceUntilIdle()

        assertTrue(
            "setDepoAlerts(true) success must trigger a refresh (fetchState called again)",
            fakeRepo.fetchStateCallCount > fetchCountBefore,
        )
    }

    // =========================================================================
    // 11. setDepoAlerts(false) success → refreshes uiState
    // =========================================================================

    @Test
    fun `setDepoAlerts false success refreshes uiState`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)
        fakeRepo.setDepoAlertsResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val fetchCountBefore = fakeRepo.fetchStateCallCount

        viewModel.setDepoAlerts(false)
        advanceUntilIdle()

        assertTrue(
            "setDepoAlerts(false) success must trigger a refresh (fetchState called again)",
            fakeRepo.fetchStateCallCount > fetchCountBefore,
        )
    }

    // =========================================================================
    // 12. close(signalId) success → emits ActionResultUiState.Success AND refreshes uiState
    // =========================================================================

    @Test
    fun `close success emits ActionResultUiState Success and refreshes uiState`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)
        fakeRepo.closeResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.close(signalId = "sig-001")
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be ActionResultUiState.Success after close() succeeds, got $result",
                result is ActionResultUiState.Success,
            )
            assertTrue(
                "fetchState must be called again after close() succeeds (refresh after close)",
                fakeRepo.fetchStateCallCount >= 2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 13. error result → ActionResultUiState.Error with message
    // =========================================================================

    @Test
    fun `start error emits ActionResultUiState Error with code and message`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)
        fakeRepo.startResult = ActionResult.Error(code = 503, message = "Service Unavailable")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.start()
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be ActionResultUiState.Error when start() fails, got $result",
                result is ActionResultUiState.Error,
            )
            val errorResult = result as ActionResultUiState.Error
            assertEquals(
                "Error code must be propagated from the ActionResult.Error",
                503,
                errorResult.code,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 14. actionResult resets to null after clearActionResult() is called
    // =========================================================================

    @Test
    fun `clearActionResult resets actionResult to null`() = runTest {
        fakeRepo.fetchStateResult = TradingControlResult.Success(sampleData)
        fakeRepo.startResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.start()
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "Must have a non-null actionResult after start() succeeds before clearing",
                result is ActionResultUiState.Success,
            )

            // Consumer calls clear after showing the snackbar
            viewModel.clearActionResult()
            advanceUntilIdle()

            val cleared = awaitItem()
            assertNull(
                "actionResult must be null after clearActionResult() is called",
                cleared,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [TradingControlRepository].
 * Configure the result properties before each test to control behaviour.
 * Call counts let tests assert how many times each operation was invoked.
 */
private class FakeTradingControlRepository : TradingControlRepository {

    var fetchStateResult: TradingControlResult =
        TradingControlResult.Error(message = "fetchStateResult not configured")
    var startResult: ActionResult = ActionResult.Success
    var stopResult: ActionResult = ActionResult.Success
    var setModeResult: ActionResult = ActionResult.Success
    var setDepoAlertsResult: ActionResult = ActionResult.Success
    var closeResult: ActionResult = ActionResult.Success

    var fetchStateCallCount: Int = 0
    var startCallCount: Int = 0
    var stopCallCount: Int = 0
    var setModeCallCount: Int = 0
    var setDepoAlertsCallCount: Int = 0
    var closeCallCount: Int = 0

    override suspend fun fetchState(): TradingControlResult {
        fetchStateCallCount++
        return fetchStateResult
    }

    override suspend fun start(): ActionResult {
        startCallCount++
        return startResult
    }

    override suspend fun stop(): ActionResult {
        stopCallCount++
        return stopResult
    }

    override suspend fun setMode(mode: String): ActionResult {
        setModeCallCount++
        return setModeResult
    }

    override suspend fun setDepoAlerts(enabled: Boolean): ActionResult {
        setDepoAlertsCallCount++
        return setDepoAlertsResult
    }

    override suspend fun close(signalId: String): ActionResult {
        closeCallCount++
        return closeResult
    }
}
