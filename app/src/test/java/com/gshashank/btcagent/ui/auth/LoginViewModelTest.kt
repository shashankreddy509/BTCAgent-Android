package com.gshashank.btcagent.ui.auth

import android.app.Activity
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseUser
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.data.repository.UserCancelledException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LoginViewModel].
 *
 * Uses [FakeAuthRepository] so no real Firebase or CredentialManager code executes.
 * All tests run on [UnconfinedTestDispatcher] to make StateFlow emissions immediate.
 * Turbine's [test] extension collects [LoginViewModel.uiState] synchronously.
 *
 * Every test is expected to FAIL until [LoginViewModel] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    // Fake repository whose sign-in outcome is configured per test.
    private lateinit var fakeRepo: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    // A mock activity reference — ViewModel passes it to the repo, so it just needs to exist.
    private val mockActivity: Activity = object : Activity() {}

    // A stub FirebaseUser — we only need an instance; no real methods are called on it.
    private val mockUser: FirebaseUser = org.mockito.kotlin.mock()

    @Before
    fun setUp() {
        fakeRepo = FakeAuthRepository()
        // ViewModel is constructed with the fakes; no Hilt involved in unit tests.
        viewModel = LoginViewModel(
            repository = fakeRepo,
            catalogRepository = FakeCatalogRepository(flagValue = false),
        )
    }

    // -------------------------------------------------------------------------
    // 1. Idle state on init
    // -------------------------------------------------------------------------

    @Test
    fun `idle state on init`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(
                "ViewModel must start in Idle state",
                LoginUiState.Idle,
                initial,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 2. Loading then Success on successful sign-in
    // -------------------------------------------------------------------------

    @Test
    fun `loading then success on sign-in`() = runTest(UnconfinedTestDispatcher()) {
        fakeRepo.signInResult = Result.success(mockUser)

        viewModel.uiState.test {
            // Consume Idle
            assertEquals(LoginUiState.Idle, awaitItem())

            viewModel.onGoogleSignIn(mockActivity)

            assertEquals("Expected Loading state", LoginUiState.Loading, awaitItem())

            val success = awaitItem()
            assertTrue("Expected Success state, got $success", success is LoginUiState.Success)
            assertEquals(
                "Success must carry the user returned by the repository",
                mockUser,
                (success as LoginUiState.Success).user,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 3. Loading then Error on repository failure
    // -------------------------------------------------------------------------

    @Test
    fun `loading then error on sign-in failure`() = runTest(UnconfinedTestDispatcher()) {
        val errorMessage = "network error"
        fakeRepo.signInResult = Result.failure(RuntimeException(errorMessage))

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())

            viewModel.onGoogleSignIn(mockActivity)

            assertEquals(LoginUiState.Loading, awaitItem())

            val error = awaitItem()
            assertTrue("Expected Error state, got $error", error is LoginUiState.Error)
            assertEquals(
                "Error message must match the exception message",
                errorMessage,
                (error as LoginUiState.Error).message,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 4. Loading then Error on user cancellation (UserCancelledException)
    // -------------------------------------------------------------------------

    @Test
    fun `loading then error on user cancel`() = runTest(UnconfinedTestDispatcher()) {
        fakeRepo.signInResult = Result.failure(UserCancelledException())

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())

            viewModel.onGoogleSignIn(mockActivity)

            assertEquals(LoginUiState.Loading, awaitItem())

            val error = awaitItem()
            assertTrue(
                "UserCancelledException must map to an Error state, got $error",
                error is LoginUiState.Error,
            )
            // The cancel message must be user-friendly (non-null, non-blank).
            val msg = (error as LoginUiState.Error).message
            assertTrue(
                "Cancel error message must not be blank, was: '$msg'",
                msg.isNotBlank(),
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // 5. Second sign-in while already in Success re-enters Loading cycle
    // -------------------------------------------------------------------------

    @Test
    fun `second sign-in re-enters loading cycle`() = runTest(UnconfinedTestDispatcher()) {
        fakeRepo.signInResult = Result.success(mockUser)

        viewModel.uiState.test {
            // First sign-in cycle: Idle → Loading → Success
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.onGoogleSignIn(mockActivity)
            assertEquals(LoginUiState.Loading, awaitItem())
            assertTrue(awaitItem() is LoginUiState.Success)

            // Second sign-in must begin a new Loading cycle — no state-machine lock-in.
            viewModel.onGoogleSignIn(mockActivity)
            val secondLoading = awaitItem()
            assertEquals(
                "Second sign-in must re-emit Loading, not stay in Success",
                LoginUiState.Loading,
                secondLoading,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // Catalog flag-gating contract (MOBILE-28)
    //
    // LoginViewModel reads CatalogFlags.LOGIN_MOCK via an injected
    // CatalogRepository and exposes the result as `val isMockLayout: Boolean`.
    // =========================================================================

    // -------------------------------------------------------------------------
    // 6. isMockLayout is false when catalog flag is OFF
    // -------------------------------------------------------------------------

    @Test
    fun `isMockLayout is false when catalog flag is off`() {
        val fakeCatalog = FakeCatalogRepository(flagValue = false)
        val vm = LoginViewModel(
            repository = fakeRepo,
            catalogRepository = fakeCatalog,
        )

        assertFalse(
            "isMockLayout must be false when ${CatalogFlags.LOGIN_MOCK} is OFF",
            vm.isMockLayout.value,
        )
    }

    // -------------------------------------------------------------------------
    // 7. isMockLayout is true when catalog flag is ON
    // -------------------------------------------------------------------------

    @Test
    fun `isMockLayout is true when catalog flag is on`() {
        val fakeCatalog = FakeCatalogRepository(flagValue = true)
        val vm = LoginViewModel(
            repository = fakeRepo,
            catalogRepository = fakeCatalog,
        )

        assertTrue(
            "isMockLayout must be true when ${CatalogFlags.LOGIN_MOCK} is ON",
            vm.isMockLayout.value,
        )
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Manual fake for [AuthRepository].
 *
 * Set [signInResult] before each test to control what [signInWithGoogle] returns.
 */
private class FakeAuthRepository : AuthRepository {

    var signInResult: Result<FirebaseUser> =
        Result.failure(IllegalStateException("signInResult not configured"))

    override val currentUser: FirebaseUser? = null

    override suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> = signInResult

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("not needed in ViewModel tests"))

    override fun signOut() {
        // no-op for ViewModel tests
    }
}

/**
 * Manual fake for [CatalogRepository].
 *
 * [flagValue] controls the return value of [isEnabled] for ALL flag ids.
 * Set to true to simulate flag ON, false for flag OFF (the rollback path).
 */
private class FakeCatalogRepository(private val flagValue: Boolean) : CatalogRepository {

    override suspend fun refresh() {
        // no-op for ViewModel tests
    }

    override fun isEnabled(id: Int): Boolean = flagValue

    override fun isEnabled(id: Int, default: Boolean): Boolean = flagValue

    override fun isEnabledFlow(id: Int, default: Boolean): kotlinx.coroutines.flow.Flow<Boolean> =
        kotlinx.coroutines.flow.flowOf(flagValue)
}
