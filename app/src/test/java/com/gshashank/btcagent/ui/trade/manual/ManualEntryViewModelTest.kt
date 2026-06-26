package com.gshashank.btcagent.ui.trade.manual

import app.cash.turbine.test
import com.gshashank.btcagent.core.biometric.BiometricResult
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.ManualOrderDraft
import com.gshashank.btcagent.data.model.OrderType
import com.gshashank.btcagent.data.model.PendingOrder
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.model.TradingControlData
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.data.repository.ManualEntryRepository
import com.gshashank.btcagent.data.repository.TradingControlRepository
import com.gshashank.btcagent.data.repository.TradingControlResult
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [ManualEntryViewModel] — MOBILE-19.
 *
 * Uses hand-written fakes for all collaborators so no real network or Android framework calls are
 * made. [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * ## Biometric flow design
 * The ViewModel does NOT call a biometric library directly. Instead:
 *   1. LIVE mode `placeMarket()` sets `pendingConfirmState` to a non-null [ManualOrderDraft].
 *   2. The UI layer (Activity/Composable) observes `pendingConfirmState`, launches the system
 *      biometric prompt, then feeds the result back via `viewModel.onBiometricResult(result)`.
 *   3. The ViewModel processes [BiometricResult]: Success → POST; Cancelled/Failed → no POST.
 *
 * This design keeps biometric-hardware concerns in the UI layer and the ViewModel
 * fully JVM-testable.
 *
 * ## Domain rules under test
 *   - Form StateFlow is initially empty (no fields set, no summary).
 *   - OrderSummary is computed reactively when qty + entry + sl are all present.
 *   - Long SL >= entry → validation error, POST blocked.
 *   - Short SL <= entry → validation error, POST blocked.
 *   - PAPER mode: `placeMarket()` calls `repo.placeMarket` directly, no pendingConfirmState.
 *   - LIVE mode: `placeMarket()` sets `pendingConfirmState`; biometric Success → POST called;
 *     Cancelled/Failed → not called.
 *   - `cancelPending(id)` calls `repo.cancelPending`; refreshes list on Success.
 *   - Double-tap guard: second `placeMarket()` while first in-flight → ignored.
 *   - 403 → adminOnlyMessage emitted (not raw error text).
 *   - Catalog flag 100007 OFF → `catalogEnabled` StateFlow emits false.
 *   - Catalog flag 100007 ON → `catalogEnabled` StateFlow emits true.
 *
 * All tests MUST fail (red) until [ManualEntryViewModel] is implemented.
 *
 * Test coverage:
 *   1.  Initial form state: all fields null, no summary
 *   2.  updateQty + updateEntry + updateSl → OrderSummary computed (notional, maxLoss)
 *   3.  updateTp → rr included in OrderSummary
 *   4.  LONG maxLoss uses (entry - sl), SHORT uses (sl - entry) — discriminator
 *   5.  Long SL >= entry → slValidationError non-null, placeMarket blocked
 *   6.  Short SL <= entry → slValidationError non-null, placeMarket blocked
 *   7.  PAPER mode: placeMarket() calls repo.placeMarket directly (no pendingConfirmState)
 *   8.  LIVE mode: placeMarket() sets pendingConfirmState (bottom-sheet trigger)
 *   9.  LIVE mode: onBiometricResult(Success) → repo.placeMarket called, pendingConfirmState clears
 *   10. LIVE mode: onBiometricResult(Cancelled) → repo.placeMarket NOT called
 *   11. LIVE mode: onBiometricResult(Failed) → repo.placeMarket NOT called
 *   12. cancelPending → repo.cancelPending called; pending list refreshed on success
 *   13. Double-tap guard: second placeMarket() while first in-flight → ignored
 *   14. 403 from placeMarket → adminOnlyMessage emitted (not raw error)
 *   15. Catalog flag MANUAL_ENTRY (100007) = false → catalogEnabled emits false
 *   16. Catalog flag MANUAL_ENTRY (100007) = true → catalogEnabled emits true
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeManualRepo: FakeManualEntryRepository
    private lateinit var fakeTradingControlRepo: FakeTradingControlRepository
    private lateinit var fakeCatalog: FakeCatalogRepository

    // -------------------------------------------------------------------------
    // Stable fixtures
    // -------------------------------------------------------------------------

    private val paperModeState = TradingControlData(
        running = true,
        mode = ExecutionMode.PAPER,
        depoAlertsEnabled = false,
        positions = emptyList(),
    )

    private val liveModeState = TradingControlData(
        running = true,
        mode = ExecutionMode.LIVE,
        depoAlertsEnabled = false,
        positions = emptyList(),
    )

    @Before
    fun setUp() {
        fakeManualRepo = FakeManualEntryRepository()
        fakeTradingControlRepo = FakeTradingControlRepository()
        fakeCatalog = FakeCatalogRepository()

        // Default: PAPER mode so most tests don't need to configure this
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(paperModeState)
    }

    private fun createViewModel(): ManualEntryViewModel =
        ManualEntryViewModel(
            manualEntryRepository = fakeManualRepo,
            tradingControlRepository = fakeTradingControlRepo,
            catalogRepository = fakeCatalog,
        )

    // =========================================================================
    // 1. Initial form state: all fields null, no summary shown
    // =========================================================================

    @Test
    fun `initial form state has all fields null and no summary`() = runTest {
        val viewModel = createViewModel()

        val form = viewModel.formState.value

        assertNull(
            "qty must be null in the initial form state before any user input",
            form.qty,
        )
        assertNull(
            "entry must be null in the initial form state",
            form.entry,
        )
        assertNull(
            "sl must be null in the initial form state",
            form.sl,
        )
        assertNull(
            "tp must be null in the initial form state",
            form.tp,
        )
        assertNull(
            "orderSummary must be null before all required fields are provided (null-render guard)",
            form.orderSummary,
        )
    }

    // =========================================================================
    // 2. updateQty + updateEntry + updateSl → OrderSummary computed
    // =========================================================================

    @Test
    fun `providing qty entry and sl triggers OrderSummary computation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")

        advanceUntilIdle()

        val form = viewModel.formState.value

        assertNotNull(
            "orderSummary must be non-null after qty, entry, and sl are all set",
            form.orderSummary,
        )
        val summary = form.orderSummary!!

        assertEquals(
            "notional must equal qty * entry = 0.01 * 50000 = 500.0",
            500.0,
            summary.notional,
            0.001,
        )
        assertEquals(
            "maxLoss for default LONG direction must be qty * (entry - sl) = 0.01 * 1000 = 10.0",
            10.0,
            summary.maxLoss,
            0.001,
        )
        assertNull(
            "rr must be null when tp is not set",
            summary.rr,
        )
    }

    // =========================================================================
    // 3. updateTp → rr included in OrderSummary when tp provided
    // =========================================================================

    @Test
    fun `updateTp causes rr to be computed when qty entry and sl are already set`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateQty("1.0")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        viewModel.updateTp("53000.0") // rr = (53000 - 50000) / (50000 - 49000) = 3.0

        advanceUntilIdle()

        val summary = viewModel.formState.value.orderSummary

        assertNotNull("orderSummary must be non-null when all fields including tp are set", summary)
        assertNotNull("rr must be non-null when tp is provided", summary!!.rr)
        assertEquals(
            "rr for LONG = (tp - entry) / (entry - sl) = (53000 - 50000)/(50000 - 49000) = 3.0",
            3.0,
            summary.rr!!,
            0.001,
        )
    }

    // =========================================================================
    // 4. LONG maxLoss uses (entry - sl), SHORT uses (sl - entry) — discriminator
    // =========================================================================

    @Test
    fun `LONG maxLoss uses entry minus sl and SHORT maxLoss uses sl minus entry NOT swapped`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Configure LONG
        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("1.0")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0") // entry - sl = 1000
        advanceUntilIdle()

        val longSummary = viewModel.formState.value.orderSummary
        assertNotNull("LONG orderSummary must be non-null", longSummary)
        assertEquals(
            "LONG maxLoss = qty * (entry - sl) = 1.0 * (50000 - 49000) = 1000.0",
            1_000.0,
            longSummary!!.maxLoss,
            0.001,
        )

        // Switch to SHORT with sl > entry
        viewModel.updateDirection(Side.Short)
        viewModel.updateSl("51500.0") // sl - entry = 1500
        advanceUntilIdle()

        val shortSummary = viewModel.formState.value.orderSummary
        assertNotNull("SHORT orderSummary must be non-null", shortSummary)
        assertEquals(
            "DISCRIMINATOR: SHORT maxLoss = qty * (sl - entry) = 1.0 * (51500 - 50000) = 1500.0 " +
                "— must NOT use LONG formula (entry - sl) which would be negative",
            1_500.0,
            shortSummary!!.maxLoss,
            0.001,
        )
    }

    // =========================================================================
    // 5. Long SL >= entry → slValidationError non-null, placeMarket blocked
    // =========================================================================

    @Test
    fun `LONG SL at or above entry produces slValidationError and blocks placeMarket`() = runTest {
        fakeManualRepo.placeMarketResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("50000.0") // SL == entry → invalid for LONG
        advanceUntilIdle()

        assertNotNull(
            "slValidationError must be non-null when LONG SL >= entry",
            viewModel.formState.value.slValidationError,
        )

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount
        viewModel.placeMarket()
        advanceUntilIdle()

        assertEquals(
            "placeMarket must NOT call repo when LONG SL >= entry (validation blocks POST)",
            placeCallsBefore,
            fakeManualRepo.placeMarketCallCount,
        )
    }

    @Test
    fun `LONG SL above entry also produces slValidationError`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("51000.0") // SL > entry → invalid for LONG
        advanceUntilIdle()

        assertNotNull(
            "slValidationError must be non-null when LONG SL > entry",
            viewModel.formState.value.slValidationError,
        )
    }

    // =========================================================================
    // 6. Short SL <= entry → slValidationError non-null, placeMarket blocked
    // =========================================================================

    @Test
    fun `SHORT SL at or below entry produces slValidationError and blocks placeMarket`() = runTest {
        fakeManualRepo.placeMarketResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Short)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("50000.0") // SL == entry → invalid for SHORT
        advanceUntilIdle()

        assertNotNull(
            "slValidationError must be non-null when SHORT SL <= entry",
            viewModel.formState.value.slValidationError,
        )

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount
        viewModel.placeMarket()
        advanceUntilIdle()

        assertEquals(
            "placeMarket must NOT call repo when SHORT SL <= entry (validation blocks POST)",
            placeCallsBefore,
            fakeManualRepo.placeMarketCallCount,
        )
    }

    @Test
    fun `SHORT SL below entry also produces slValidationError`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Short)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0") // SL < entry → invalid for SHORT
        advanceUntilIdle()

        assertNotNull(
            "slValidationError must be non-null when SHORT SL < entry",
            viewModel.formState.value.slValidationError,
        )
    }

    // =========================================================================
    // 7. PAPER mode: placeMarket() calls repo.placeMarket directly without pendingConfirmState
    // =========================================================================

    @Test
    fun `PAPER mode placeMarket calls repo directly without setting pendingConfirmState`() = runTest {
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(paperModeState)
        fakeManualRepo.placeMarketResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        advanceUntilIdle()

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount

        viewModel.placeMarket()
        advanceUntilIdle()

        assertEquals(
            "PAPER mode: placeMarket() must call repo.placeMarket exactly once",
            placeCallsBefore + 1,
            fakeManualRepo.placeMarketCallCount,
        )
        assertNull(
            "PAPER mode: pendingConfirmState must remain null — no biometric bottom sheet needed",
            viewModel.pendingConfirmState.value,
        )
    }

    // =========================================================================
    // 8. LIVE mode: placeMarket() sets pendingConfirmState (bottom-sheet trigger)
    //    and does NOT call repo yet
    // =========================================================================

    @Test
    fun `LIVE mode placeMarket sets pendingConfirmState and does NOT call repo`() = runTest {
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(liveModeState)
        fakeManualRepo.placeMarketResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        advanceUntilIdle()

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount

        viewModel.pendingConfirmState.test {
            awaitItem() // consume initial null

            viewModel.placeMarket()
            advanceUntilIdle()

            val pendingDraft = awaitItem()
            assertNotNull(
                "LIVE mode: pendingConfirmState must emit a non-null ManualOrderDraft after placeMarket()",
                pendingDraft,
            )
            assertEquals(
                "LIVE mode: repo.placeMarket must NOT be called before biometric confirmation",
                placeCallsBefore,
                fakeManualRepo.placeMarketCallCount,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 9. LIVE mode: onBiometricResult(Success) → repo.placeMarket called, clears state
    // =========================================================================

    @Test
    fun `LIVE mode biometric Success calls repo placeMarket and clears pendingConfirmState`() = runTest {
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(liveModeState)
        fakeManualRepo.placeMarketResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        advanceUntilIdle()

        // Initiate LIVE order — sets pendingConfirmState
        viewModel.placeMarket()
        advanceUntilIdle()

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount

        // Simulate biometric success (user authenticated)
        viewModel.onBiometricResult(BiometricResult.Success)
        advanceUntilIdle()

        assertEquals(
            "LIVE mode + biometric Success: repo.placeMarket must be called exactly once",
            placeCallsBefore + 1,
            fakeManualRepo.placeMarketCallCount,
        )
        assertNull(
            "pendingConfirmState must be null after biometric result is processed",
            viewModel.pendingConfirmState.value,
        )
    }

    // =========================================================================
    // 10. LIVE mode: onBiometricResult(Cancelled) → repo.placeMarket NOT called
    // =========================================================================

    @Test
    fun `LIVE mode biometric Cancelled does NOT call repo placeMarket`() = runTest {
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(liveModeState)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        advanceUntilIdle()

        viewModel.placeMarket()
        advanceUntilIdle()

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount

        viewModel.onBiometricResult(BiometricResult.Cancelled)
        advanceUntilIdle()

        assertEquals(
            "LIVE mode + biometric Cancelled: repo.placeMarket must NOT be called",
            placeCallsBefore,
            fakeManualRepo.placeMarketCallCount,
        )
    }

    // =========================================================================
    // 11. LIVE mode: onBiometricResult(Failed) → repo.placeMarket NOT called
    // =========================================================================

    @Test
    fun `LIVE mode biometric Failed does NOT call repo placeMarket`() = runTest {
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(liveModeState)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        advanceUntilIdle()

        viewModel.placeMarket()
        advanceUntilIdle()

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount

        viewModel.onBiometricResult(BiometricResult.Failed)
        advanceUntilIdle()

        assertEquals(
            "LIVE mode + biometric Failed: repo.placeMarket must NOT be called",
            placeCallsBefore,
            fakeManualRepo.placeMarketCallCount,
        )
    }

    // =========================================================================
    // 12. cancelPending → repo.cancelPending called; pending list refreshed on success
    // =========================================================================

    @Test
    fun `cancelPending calls repo cancelPending and refreshes pending list on success`() = runTest {
        fakeManualRepo.cancelPendingResult = ActionResult.Success
        fakeManualRepo.fetchPendingResult = emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val cancelCallsBefore = fakeManualRepo.cancelPendingCallCount
        val fetchCallsBefore = fakeManualRepo.fetchPendingCallCount

        viewModel.cancelPending("limit-001")
        advanceUntilIdle()

        assertEquals(
            "cancelPending() must call repo.cancelPending exactly once",
            cancelCallsBefore + 1,
            fakeManualRepo.cancelPendingCallCount,
        )
        assertTrue(
            "cancelPending() success must trigger fetchPending to refresh the pending order list",
            fakeManualRepo.fetchPendingCallCount > fetchCallsBefore,
        )
    }

    @Test
    fun `cancelPending failure does NOT refresh pending list`() = runTest {
        fakeManualRepo.cancelPendingResult = ActionResult.Error(code = 404, message = "Not found")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val fetchCallsBefore = fakeManualRepo.fetchPendingCallCount

        viewModel.cancelPending("missing-id")
        advanceUntilIdle()

        assertEquals(
            "cancelPending() failure must NOT trigger fetchPending (no stale refresh on error)",
            fetchCallsBefore,
            fakeManualRepo.fetchPendingCallCount,
        )
    }

    // =========================================================================
    // 13. Double-tap guard: second placeMarket() while first is in-flight → ignored
    // =========================================================================

    @Test
    fun `double-tap guard second placeMarket while first is in-flight is ignored`() = runTest {
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(paperModeState)
        fakeManualRepo.placeMarketResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        advanceUntilIdle()

        val placeCallsBefore = fakeManualRepo.placeMarketCallCount

        // Fire two calls without letting the first complete
        viewModel.placeMarket()
        viewModel.placeMarket() // second call must be no-op
        advanceUntilIdle()

        assertEquals(
            "Double-tap guard: repo.placeMarket must only be called once even with two rapid taps",
            placeCallsBefore + 1,
            fakeManualRepo.placeMarketCallCount,
        )
    }

    // =========================================================================
    // 14. 403 from placeMarket → adminOnlyMessage emitted (not raw error text)
    // =========================================================================

    @Test
    fun `placeMarket 403 emits adminOnlyMessage and NOT raw error text`() = runTest {
        fakeTradingControlRepo.fetchStateResult = TradingControlResult.Success(paperModeState)
        fakeManualRepo.placeMarketResult = ActionResult.Error(
            code = 403,
            message = "Manual trading isn't enabled for your account yet",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDirection(Side.Long)
        viewModel.updateQty("0.01")
        viewModel.updateEntry("50000.0")
        viewModel.updateSl("49000.0")
        advanceUntilIdle()

        viewModel.adminOnlyMessage.test {
            awaitItem() // consume initial null

            viewModel.placeMarket()
            advanceUntilIdle()

            val message = awaitItem()
            assertNotNull(
                "adminOnlyMessage must emit a non-null string when placeMarket() returns a 403 error",
                message,
            )
            assertTrue(
                "adminOnlyMessage must contain the user-friendly admin-only message text, got '$message'",
                message!!.isNotBlank(),
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 15. Catalog flag MANUAL_ENTRY (100007) = false → catalogEnabled emits false
    //
    // Rollback contract: when MANUAL_ENTRY flag is OFF/absent, the tile and form must be
    // hidden — this is the OLD/absent behavior (feature not shown).
    // =========================================================================

    @Test
    fun `catalogEnabled emits false when MANUAL_ENTRY catalog flag id 100007 is OFF`() = runTest {
        fakeCatalog.manualEntryEnabled = false

        val viewModel = createViewModel()

        viewModel.catalogEnabled.test {
            advanceUntilIdle()

            val emission = awaitItem()
            assertFalse(
                "catalogEnabled must emit false when CatalogFlags.MANUAL_ENTRY (id=100007) is OFF — " +
                    "Manual Entry tile and form must be hidden (old/absent behavior, rollback path)",
                emission,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 16. Catalog flag MANUAL_ENTRY (100007) = true → catalogEnabled emits true
    //
    // Rollback contract: when MANUAL_ENTRY flag is ON, the tile and form must be visible —
    // this is the NEW behavior.
    // =========================================================================

    @Test
    fun `catalogEnabled emits true when MANUAL_ENTRY catalog flag id 100007 is ON`() = runTest {
        fakeCatalog.manualEntryEnabled = true

        val viewModel = createViewModel()

        viewModel.catalogEnabled.test {
            advanceUntilIdle()

            val emission = awaitItem()
            assertTrue(
                "catalogEnabled must emit true when CatalogFlags.MANUAL_ENTRY (id=100007) is ON — " +
                    "Manual Entry tile and form must be visible (new behavior)",
                emission,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `catalogEnabled defaults to false when MANUAL_ENTRY flag is absent from catalog`() = runTest {
        // Flag not configured → absent → default=false (not security-sensitive)
        fakeCatalog.manualEntryEnabled = false

        val viewModel = createViewModel()

        viewModel.catalogEnabled.test {
            advanceUntilIdle()

            val emission = awaitItem()
            assertFalse(
                "catalogEnabled must default to false when MANUAL_ENTRY flag is absent — " +
                    "hidden is the safe fallback (MANUAL_ENTRY flag is not security-sensitive, default=false)",
                emission,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [ManualEntryRepository].
 * Configure result properties before each test.
 * Call counts let tests assert how many times each operation was invoked.
 */
private class FakeManualEntryRepository : ManualEntryRepository {

    var placeMarketResult: ActionResult = ActionResult.Success
    var placeLimitResult: ActionResult = ActionResult.Success
    var cancelPendingResult: ActionResult = ActionResult.Success
    var fetchPendingResult: List<PendingOrder> = emptyList()

    var placeMarketCallCount: Int = 0
    var placeLimitCallCount: Int = 0
    var cancelPendingCallCount: Int = 0
    var fetchPendingCallCount: Int = 0

    override suspend fun placeMarket(draft: ManualOrderDraft): ActionResult {
        placeMarketCallCount++
        return placeMarketResult
    }

    override suspend fun placeLimit(draft: ManualOrderDraft): ActionResult {
        placeLimitCallCount++
        return placeLimitResult
    }

    override suspend fun cancelPending(pendingId: String): ActionResult {
        cancelPendingCallCount++
        return cancelPendingResult
    }

    override suspend fun fetchPending(): List<PendingOrder> {
        fetchPendingCallCount++
        return fetchPendingResult
    }
}

/**
 * Hand-written fake [TradingControlRepository] that only implements [fetchState].
 * Other methods are no-ops returning [ActionResult.Success].
 */
private class FakeTradingControlRepository : TradingControlRepository {

    var fetchStateResult: TradingControlResult =
        TradingControlResult.Error(message = "fetchStateResult not configured")

    var fetchStateCallCount: Int = 0

    override suspend fun fetchState(): TradingControlResult {
        fetchStateCallCount++
        return fetchStateResult
    }

    override suspend fun start(): ActionResult = ActionResult.Success
    override suspend fun stop(): ActionResult = ActionResult.Success
    override suspend fun setMode(mode: String): ActionResult = ActionResult.Success
    override suspend fun setDepoAlerts(enabled: Boolean): ActionResult = ActionResult.Success
    override suspend fun close(signalId: String): ActionResult = ActionResult.Success
}

/**
 * Hand-written fake [CatalogRepository] for [ManualEntryViewModel] tests.
 *
 * [manualEntryEnabled] controls the [isEnabled] and [isEnabledFlow] responses for
 * [CatalogFlags.MANUAL_ENTRY] (id = 100007).
 * [manualEntryFlow] allows supplying a multi-emission flow (e.g. for toggling tests).
 * All other flag ids return false.
 */
private class FakeCatalogRepository : CatalogRepository {

    var manualEntryEnabled: Boolean = false

    /** Override to simulate a late-landing catalog fetch (emits false then true). */
    var manualEntryFlow: Flow<Boolean>? = null

    override suspend fun refresh() {
        // No-op in tests — tests configure results directly via properties.
    }

    override fun isEnabledFlow(id: Int, default: Boolean): Flow<Boolean> {
        if (id == CatalogFlags.MANUAL_ENTRY) {
            return manualEntryFlow ?: flowOf(manualEntryEnabled)
        }
        return flowOf(default)
    }

    override fun isEnabled(id: Int): Boolean {
        if (id == CatalogFlags.MANUAL_ENTRY) return manualEntryEnabled
        return false
    }

    override fun isEnabled(id: Int, default: Boolean): Boolean {
        if (id == CatalogFlags.MANUAL_ENTRY) return manualEntryEnabled
        return default
    }
}
