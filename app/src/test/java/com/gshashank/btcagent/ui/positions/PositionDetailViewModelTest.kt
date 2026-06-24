package com.gshashank.btcagent.ui.positions

import app.cash.turbine.test
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessResult
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.PositionsRepository
import com.gshashank.btcagent.data.repository.PositionsResult
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [PositionDetailViewModel] — MOBILE-6.
 *
 * Uses hand-written fakes ([FakeDetailPositionsRepository], [FakeDetailAccessRepository]) so
 * no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [UnconfinedTestDispatcher] as [Dispatchers.Main] so that
 * [viewModelScope]-backed coroutines are driven synchronously. NO real time waits — all
 * coroutine advancement is via [advanceUntilIdle] (MOBILE-32).
 *
 * All tests MUST fail (red) until [PositionDetailViewModel] is implemented.
 *
 * Test coverage:
 *   1. Ready for a given signalId (filtered from full positions list).
 *   2. isAdmin=true → canEdit=true in the ViewModel.
 *   3. isAdmin=false → canEdit=false in the ViewModel.
 *   4. close() success → actionState.Success + refresh triggered.
 *   5. close() network error → actionState.Error.
 *   6. editTpSl() 200 → actionState.Success.
 *   7. editTpSl() 403 → actionState.Error with code 403.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeDetailPositionsRepository
    private lateinit var fakeAccess: FakeDetailAccessRepository

    private val targetSignalId = "sig-001"

    private val targetPosition = Position(
        signalId = targetSignalId,
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

    private val otherPosition = Position(
        signalId = "sig-999",
        side = Side.Short,
        entryPrice = 60_000.0,
        currentPrice = 59_000.0,
        qty = 1.0,
        sl = 61_000.0,
        tp = 57_000.0,
        status = "open",
        openedAt = "2026-06-24T08:00:00Z",
        pnl = 1.0,
        pnlPct = 1.0,
        contractSize = 0.001,
    )

    @Before
    fun setUp() {
        fakeRepo = FakeDetailPositionsRepository()
        fakeAccess = FakeDetailAccessRepository()
        // Default: positions list contains both positions; access is non-admin
        fakeRepo.fetchPositionsResult =
            PositionsResult.Success(listOf(targetPosition, otherPosition))
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = false)
    }

    private fun createViewModel(signalId: String = targetSignalId): PositionDetailViewModel =
        PositionDetailViewModel(
            signalId = signalId,
            repository = fakeRepo,
            accessRepository = fakeAccess,
        )

    // =========================================================================
    // 1. Ready for a given signalId filtered from full list
    // =========================================================================

    @Test
    fun `uiState becomes Ready with position matching the given signalId`() = runTest {
        val viewModel = createViewModel(signalId = targetSignalId)

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("State must be Ready, got $ready", ready is UiState.Ready)
            val position = (ready as UiState.Ready<Position>).data
            assertEquals(
                "position.signalId must match the requested signalId",
                targetSignalId,
                position.signalId,
            )
            assertEquals(
                "position.side must be Long",
                Side.Long,
                position.side,
            )
            assertEquals(
                "position.entryPrice must match",
                50_000.0,
                position.entryPrice,
                0.0001,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown signalId emits UiState Error`() = runTest {
        val viewModel = createViewModel(signalId = "does-not-exist")

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "Unknown signalId must produce UiState.Error (position not found), got $state",
                state is UiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 2. isAdmin=true → canEdit=true
    // =========================================================================

    @Test
    fun `isAdmin true exposes canEdit true`() = runTest {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = true)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertTrue(
            "canEdit must be true when the user is an admin",
            viewModel.canEdit.value,
        )
    }

    // =========================================================================
    // 3. isAdmin=false → canEdit=false
    // =========================================================================

    @Test
    fun `isAdmin false exposes canEdit false`() = runTest {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = false)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertFalse(
            "canEdit must be false when the user is not an admin",
            viewModel.canEdit.value,
        )
    }

    // =========================================================================
    // 4. close() success → actionState.Success + refresh triggered
    // =========================================================================

    @Test
    fun `close success emits actionState Success and triggers data refresh`() = runTest {
        fakeRepo.closeResult = ActionResult.Success

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.actionState.test {
            // Initial idle emission (no action in flight)
            awaitItem() // consume initial null/idle state

            viewModel.close()
            advanceUntilIdle()

            val action = awaitItem()
            assertTrue(
                "actionState must be Success after close() returns 200, got $action",
                action is ActionResultUiState.Success,
            )

            // After success the ViewModel must refresh: fetchPositions called again
            assertTrue(
                "fetchPositions must have been called more than once after close() succeeds — indicates refresh",
                fakeRepo.fetchPositionsCallCount >= 2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. close() error → actionState.Error
    // =========================================================================

    @Test
    fun `close error emits actionState Error`() = runTest {
        fakeRepo.closeResult = ActionResult.Error(code = 404, message = "position not found")

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.actionState.test {
            awaitItem() // consume initial idle state

            viewModel.close()
            advanceUntilIdle()

            val action = awaitItem()
            assertTrue(
                "actionState must be Error when close() returns a non-200 code, got $action",
                action is ActionResultUiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 6. editTpSl() success → actionState.Success
    // =========================================================================

    @Test
    fun `editTpSl success emits actionState Success`() = runTest {
        // editTpSl is admin-gated in the ViewModel — must be admin to reach the repository.
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = true)
        fakeRepo.editTpSlResult = ActionResult.Success

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.actionState.test {
            awaitItem() // consume initial idle state

            viewModel.editTpSl(sl = 48_500.0, tp = 54_000.0)
            advanceUntilIdle()

            val action = awaitItem()
            assertTrue(
                "actionState must be Success when editTpSl() returns 200, got $action",
                action is ActionResultUiState.Success,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 7. editTpSl() 403 → actionState.Error with code 403
    // =========================================================================

    @Test
    fun `editTpSl 403 emits actionState Error with code 403`() = runTest {
        // Admin client (passes the local guard) but the SERVER returns 403 — verifies the
        // repository's 403 is surfaced, not just the local admin gate.
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = true)
        fakeRepo.editTpSlResult =
            ActionResult.Error(code = 403, message = "Forbidden — admin only")

        val viewModel = createViewModel()

        advanceUntilIdle()

        viewModel.actionState.test {
            awaitItem() // consume initial idle state

            viewModel.editTpSl(sl = 48_500.0, tp = 54_000.0)
            advanceUntilIdle()

            val action = awaitItem()
            assertTrue(
                "actionState must be Error for a 403 response from editTpSl(), got $action",
                action is ActionResultUiState.Error,
            )
            val errorAction = action as ActionResultUiState.Error
            assertEquals(
                "error code must be 403",
                403,
                errorAction.code,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

private class FakeDetailPositionsRepository : PositionsRepository {

    var fetchPositionsResult: PositionsResult =
        PositionsResult.Error(message = "fetchPositionsResult not configured")

    var closeResult: ActionResult = ActionResult.Success
    var editTpSlResult: ActionResult = ActionResult.Success

    var fetchPositionsCallCount: Int = 0

    override suspend fun fetchPositions(): PositionsResult {
        fetchPositionsCallCount++
        return fetchPositionsResult
    }

    override suspend fun close(signalId: String): ActionResult = closeResult

    override suspend fun editTpSl(signalId: String, sl: Double?, tp: Double?): ActionResult =
        editTpSlResult
}

private class FakeDetailAccessRepository : AccessRepository {

    var checkAccessResult: AccessResult =
        AccessResult.Error(cause = IllegalStateException("checkAccessResult not configured"))

    override suspend fun checkAccess(): AccessResult = checkAccessResult
}
