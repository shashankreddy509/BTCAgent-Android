package com.gshashank.btcagent.ui.gate

import android.app.Activity
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseUser
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessResult
import com.gshashank.btcagent.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [GateViewModel].
 *
 * Uses [FakeAccessRepository] and [FakeGateAuthRepository] (hand-written fakes) so no real
 * Firebase or network calls are made.
 * [UnconfinedTestDispatcher] is substituted for `@MainDispatcher` to keep StateFlow emissions
 * synchronous and to drive coroutines inside [runTest].
 * Turbine's [test] extension collects [GateViewModel.uiState] synchronously.
 *
 * All tests are expected to FAIL until [GateViewModel] is implemented.
 *
 * Test coverage:
 *   1. init emits Loading then Allowed.
 *   2. init emits Loading then Pending with the signed-in user's email.
 *   3. init emits Loading then Unauthorized.
 *   4. init emits Loading then Error.
 *   5. onRetry resets state to Loading and re-runs the access check.
 *   6. onSignOut calls authRepository.signOut() and emits Unauthorized.
 *   7. Pending email is empty string when currentUser is null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GateViewModelTest {

    private lateinit var fakeAccess: FakeAccessRepository
    private lateinit var fakeAuth: FakeGateAuthRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    // A mockito-kotlin mock for FirebaseUser so we can stub .email without subclassing.
    private val mockUser: FirebaseUser = mock()

    @Before
    fun setUp() {
        fakeAccess = FakeAccessRepository()
        fakeAuth = FakeGateAuthRepository()
    }

    private fun createViewModel(): GateViewModel =
        GateViewModel(
            accessRepository = fakeAccess,
            authRepository = fakeAuth,
            mainDispatcher = testDispatcher,
        )

    // -------------------------------------------------------------------------
    // 1. init emits Loading then Allowed
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Loading then Allowed`() = runTest(testDispatcher) {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = false)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", GateUiState.Loading, awaitItem())
            assertEquals("Second emission must be Allowed", GateUiState.Allowed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 2. init emits Loading then Pending with email
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Loading then Pending with user email`() = runTest(testDispatcher) {
        fakeAccess.checkAccessResult = AccessResult.Pending
        // Set up a mock FirebaseUser whose email() returns the expected address.
        whenever(mockUser.email).thenReturn("user@example.com")
        fakeAuth.stubbedCurrentUser = mockUser
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", GateUiState.Loading, awaitItem())
            val pending = awaitItem()
            assertTrue("Second emission must be Pending, got $pending", pending is GateUiState.Pending)
            assertEquals(
                "Pending email must match the signed-in user email",
                "user@example.com",
                (pending as GateUiState.Pending).email,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 3. init emits Loading then Unauthorized
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Loading then Unauthorized`() = runTest(testDispatcher) {
        fakeAccess.checkAccessResult = AccessResult.Unauthorized
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", GateUiState.Loading, awaitItem())
            assertEquals("Second emission must be Unauthorized", GateUiState.Unauthorized, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 4. init emits Loading then Error
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Loading then Error`() = runTest(testDispatcher) {
        fakeAccess.checkAccessResult = AccessResult.Error(cause = null)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", GateUiState.Loading, awaitItem())
            assertEquals("Second emission must be Error", GateUiState.Error, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 5. onRetry resets to Loading and re-runs the access check
    // -------------------------------------------------------------------------

    @Test
    fun `onRetry resets to Loading and re-runs access check`() = runTest(testDispatcher) {
        // Start in Error state.
        fakeAccess.checkAccessResult = AccessResult.Error(cause = null)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Consume the initial Loading + Error cycle.
            assertEquals(GateUiState.Loading, awaitItem())
            assertEquals(GateUiState.Error, awaitItem())

            // Change the repository result before triggering the retry.
            fakeAccess.checkAccessResult = AccessResult.Allowed(admin = false)

            viewModel.onRetry()

            // Retry must reset to Loading first.
            assertEquals(
                "onRetry must re-emit Loading before the new result",
                GateUiState.Loading,
                awaitItem(),
            )
            // Then emit the new result (Allowed in this case).
            assertEquals(
                "onRetry must emit the new access result after Loading",
                GateUiState.Allowed,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 6. onSignOut calls signOut and emits Unauthorized
    // -------------------------------------------------------------------------

    @Test
    fun `onSignOut calls authRepository signOut and emits Unauthorized`() = runTest(testDispatcher) {
        fakeAccess.checkAccessResult = AccessResult.Allowed(admin = false)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Consume initial Loading + Allowed cycle.
            assertEquals(GateUiState.Loading, awaitItem())
            assertEquals(GateUiState.Allowed, awaitItem())

            viewModel.onSignOut()

            assertEquals(
                "onSignOut must emit Unauthorized",
                GateUiState.Unauthorized,
                awaitItem(),
            )
            assertTrue(
                "authRepository.signOut() must have been called",
                fakeAuth.signOutCalled,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 7. Pending email is empty string when currentUser is null
    // -------------------------------------------------------------------------

    @Test
    fun `Pending email is empty string when currentUser is null`() = runTest(testDispatcher) {
        fakeAccess.checkAccessResult = AccessResult.Pending
        fakeAuth.stubbedCurrentUser = null  // explicitly no signed-in user
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(GateUiState.Loading, awaitItem())
            val pending = awaitItem()
            assertTrue("State must be Pending, got $pending", pending is GateUiState.Pending)
            assertEquals(
                "Email must be empty string when currentUser is null",
                "",
                (pending as GateUiState.Pending).email,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Configurable fake [AccessRepository].
 * Set [checkAccessResult] before each test to control what [checkAccess] returns.
 */
private class FakeAccessRepository : AccessRepository {
    var checkAccessResult: AccessResult =
        AccessResult.Error(IllegalStateException("checkAccessResult not configured"))

    override suspend fun checkAccess(): AccessResult = checkAccessResult
}

/**
 * Fake [AuthRepository] for Gate ViewModel tests.
 *
 * [stubbedCurrentUser] — set to a mock [FirebaseUser] (with email stubbed) or null.
 * [signOutCalled]      — tracks whether [signOut] was invoked by the ViewModel.
 */
private class FakeGateAuthRepository : AuthRepository {

    var signOutCalled = false

    /**
     * Controls [currentUser]. Tests set this to a mock [FirebaseUser] whose `email` property
     * has been stubbed via `whenever(mockUser.email).thenReturn(...)`, or to null.
     */
    var stubbedCurrentUser: FirebaseUser? = null

    override val currentUser: FirebaseUser?
        get() = stubbedCurrentUser

    override suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> =
        Result.failure(UnsupportedOperationException("not needed in GateViewModel tests"))

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("not needed in GateViewModel tests"))

    override fun signOut() {
        signOutCalled = true
    }
}
