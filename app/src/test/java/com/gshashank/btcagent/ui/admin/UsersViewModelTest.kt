package com.gshashank.btcagent.ui.admin

import app.cash.turbine.test
import com.gshashank.btcagent.data.model.AdminUser
import com.gshashank.btcagent.data.model.AdminUsersData
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.AdminUsersResult
import com.gshashank.btcagent.data.repository.UsersRepository
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
 * JVM unit tests for [UsersViewModel] — MOBILE-21.
 *
 * Uses [FakeUsersRepository] (hand-written fake) so no real network or Android
 * framework calls are made.
 *
 * [MainDispatcherRule] installs [UnconfinedTestDispatcher] as [Dispatchers.Main] so that
 * [viewModelScope]-backed coroutines are driven synchronously.
 *
 * All tests MUST fail (red) until [UsersViewModel] is implemented.
 *
 * Test coverage:
 *   1.  Initial state is UiState.Loading.
 *   2.  fetchUsers() success → UiState.Ready with correct pending/active split.
 *   3.  fetchUsers() error → UiState.Error.
 *   4.  approve() success → emits ActionResultUiState.Success AND refreshes state.
 *   5.  approve() double-tap guard: second call while first is in-flight is ignored.
 *   6.  reject() success → emits ActionResultUiState.Success AND refreshes state.
 *   7.  setMode() success → refreshes state.
 *   8.  stop() success → refreshes state.
 *   9.  403 admin-only → ActionResultUiState.Error with "Admin access required".
 *   10. Empty pending list (all active) → pending is empty, not null.
 *   11. Empty active list (all pending) → active is empty, not null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeUsersRepository

    // -------------------------------------------------------------------------
    // Stable domain fixtures
    // -------------------------------------------------------------------------

    private val pendingUser = AdminUser(
        uid = "uid-pending",
        email = "pending@example.com",
        displayName = "Pending User",
        mode = ExecutionMode.PAPER,
        scannerRunning = false,
    )

    private val activeUser = AdminUser(
        uid = "uid-active",
        email = "active@example.com",
        displayName = "Active User",
        mode = ExecutionMode.LIVE,
        scannerRunning = true,
    )

    private val sampleData = AdminUsersData(
        pending = listOf(pendingUser),
        active = listOf(activeUser),
    )

    private val allPendingData = AdminUsersData(
        pending = listOf(pendingUser),
        active = emptyList(),
    )

    private val allActiveData = AdminUsersData(
        pending = emptyList(),
        active = listOf(activeUser),
    )

    @Before
    fun setUp() {
        fakeRepo = FakeUsersRepository()
    }

    // The VM no longer fetches from init{} — the screen drives refresh(isAdmin) once admin is
    // confirmed. Tests mirror that: construct, then kick off the admin-gated load.
    private fun createViewModel(): UsersViewModel =
        UsersViewModel(repository = fakeRepo).also { it.refresh(isAdmin = true) }

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
    // 2. fetchUsers() success → UiState.Ready with correct pending/active split
    // =========================================================================

    @Test
    fun `fetchUsers success emits UiState Ready with correct pending active split`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue(
                "Second emission must be UiState.Ready when repo returns Success, got $ready",
                ready is UiState.Ready<*>,
            )
            val data = (ready as UiState.Ready<AdminUsersData>).data

            assertEquals("pending list must have 1 user", 1, data.pending.size)
            assertEquals("active list must have 1 user", 1, data.active.size)
            assertEquals("pending user email must match", "pending@example.com", data.pending[0].email)
            assertEquals("active user email must match", "active@example.com", data.active[0].email)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. fetchUsers() error → UiState.Error
    // =========================================================================

    @Test
    fun `fetchUsers error emits UiState Error`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Error(message = "network unavailable")

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
    // 4. approve() success → emits ActionResultUiState.Success AND refreshes state
    // =========================================================================

    @Test
    fun `approve success emits ActionResultUiState Success and refreshes state`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)
        fakeRepo.approveResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle() // allow initial fetch to complete

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.approve("pending@example.com")
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be ActionResultUiState.Success after approve() succeeds, got $result",
                result is ActionResultUiState.Success,
            )
            assertTrue(
                "fetchUsers must be called again after approve() succeeds (at least 2 total calls)",
                fakeRepo.fetchUsersCallCount >= 2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. approve() double-tap guard
    // =========================================================================

    @Test
    fun `second approve call while first is in-flight is ignored (double-tap guard)`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)
        fakeRepo.approveResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val approveCallsBefore = fakeRepo.approveCallCount

        // Fire two consecutive calls without letting them complete between calls
        viewModel.approve("pending@example.com")
        viewModel.approve("pending@example.com")
        advanceUntilIdle()

        assertEquals(
            "Double-tap guard: approve() must only be forwarded to the repo once, not twice",
            approveCallsBefore + 1,
            fakeRepo.approveCallCount,
        )
    }

    // =========================================================================
    // 6. reject() success → emits ActionResultUiState.Success AND refreshes state
    // =========================================================================

    @Test
    fun `reject success emits ActionResultUiState Success and refreshes state`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)
        fakeRepo.rejectResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.reject("active@example.com")
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be ActionResultUiState.Success after reject() succeeds, got $result",
                result is ActionResultUiState.Success,
            )
            assertTrue(
                "fetchUsers must be called again after reject() succeeds",
                fakeRepo.fetchUsersCallCount >= 2,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 7. setMode() success → refreshes state
    // =========================================================================

    @Test
    fun `setMode success refreshes state`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)
        fakeRepo.setModeResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val fetchCountBefore = fakeRepo.fetchUsersCallCount

        viewModel.setMode(uid = "uid-active", mode = ExecutionMode.PAPER)
        advanceUntilIdle()

        assertTrue(
            "setMode() success must trigger a refresh (fetchUsers called again)",
            fakeRepo.fetchUsersCallCount > fetchCountBefore,
        )
    }

    // =========================================================================
    // 8. stop() success → refreshes state
    // =========================================================================

    @Test
    fun `stop success refreshes state`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)
        fakeRepo.stopResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val fetchCountBefore = fakeRepo.fetchUsersCallCount

        viewModel.stop(uid = "uid-active")
        advanceUntilIdle()

        assertTrue(
            "stop() success must trigger a refresh (fetchUsers called again)",
            fakeRepo.fetchUsersCallCount > fetchCountBefore,
        )
    }

    // =========================================================================
    // 9. 403 admin-only → ActionResultUiState.Error with "Admin access required"
    // =========================================================================

    @Test
    fun `admin only error from approve emits ActionResultUiState Error with admin access required message`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)
        fakeRepo.approveResult = ActionResult.Error(code = 403, message = "Admin access required")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.approve("pending@example.com")
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be ActionResultUiState.Error on 403, got $result",
                result is ActionResultUiState.Error,
            )
            val error = result as ActionResultUiState.Error
            assertTrue(
                "Error message must contain 'Admin access required', got '${error.message}'",
                error.message.contains("Admin access required", ignoreCase = true),
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 10. Empty pending list (all active)
    // =========================================================================

    @Test
    fun `when all users are active pending list is empty not null`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(allActiveData)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be Ready", state is UiState.Ready<*>)
        val data = (state as UiState.Ready<AdminUsersData>).data

        assertEquals("pending list must be empty (not null) when all users are active", 0, data.pending.size)
        assertEquals("active list must have 1 user", 1, data.active.size)
    }

    // =========================================================================
    // 11. Empty active list (all pending)
    // =========================================================================

    @Test
    fun `when all users are pending active list is empty not null`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(allPendingData)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be Ready", state is UiState.Ready<*>)
        val data = (state as UiState.Ready<AdminUsersData>).data

        assertEquals("active list must be empty (not null) when all users are pending", 0, data.active.size)
        assertEquals("pending list must have 1 user", 1, data.pending.size)
    }

    // =========================================================================
    // Security (C1): refresh(isAdmin=false) must NOT hit the admin endpoints.
    // =========================================================================

    @Test
    fun `refresh with isAdmin false does not call the admin endpoints`() = runTest {
        fakeRepo.fetchUsersResult = AdminUsersResult.Success(sampleData)
        // Construct WITHOUT the admin-confirmed load (bypass the createViewModel helper).
        val viewModel = UsersViewModel(repository = fakeRepo)

        viewModel.refresh(isAdmin = false)
        advanceUntilIdle()

        assertEquals(
            "A non-admin refresh must never call fetchUsers (no GET /api/admin/* fired)",
            0,
            fakeRepo.fetchUsersCallCount,
        )
        assertEquals(
            "uiState must stay Loading when refresh is skipped for a non-admin",
            UiState.Loading,
            viewModel.uiState.value,
        )
    }
}

// =============================================================================
// Fake repository
// =============================================================================

class FakeUsersRepository : UsersRepository {

    var fetchUsersResult: AdminUsersResult = AdminUsersResult.Success(AdminUsersData(emptyList(), emptyList()))
    var approveResult: ActionResult = ActionResult.Success
    var rejectResult: ActionResult = ActionResult.Success
    var setModeResult: ActionResult = ActionResult.Success
    var stopResult: ActionResult = ActionResult.Success

    var fetchUsersCallCount = 0
    var approveCallCount = 0
    var rejectCallCount = 0
    var setModeCallCount = 0
    var stopCallCount = 0

    override suspend fun fetchUsers(): AdminUsersResult {
        fetchUsersCallCount++
        return fetchUsersResult
    }

    override suspend fun approve(email: String): ActionResult {
        approveCallCount++
        return approveResult
    }

    override suspend fun reject(email: String): ActionResult {
        rejectCallCount++
        return rejectResult
    }

    override suspend fun setMode(uid: String, mode: ExecutionMode): ActionResult {
        setModeCallCount++
        return setModeResult
    }

    override suspend fun stop(uid: String): ActionResult {
        stopCallCount++
        return stopResult
    }
}
