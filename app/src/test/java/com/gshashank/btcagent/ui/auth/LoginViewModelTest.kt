package com.gshashank.btcagent.ui.auth

import android.app.Activity
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseUser
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.data.repository.UserCancelledException
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [LoginViewModel].
 *
 * Uses [FakeAuthRepository] so no real Firebase or CredentialManager code executes.
 * Uses [MainDispatcherRule] to install [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so that [viewModelScope]-backed coroutines run
 * synchronously without needing to pass a dispatcher into the ViewModel constructor.
 * Turbine's [test] extension collects [LoginViewModel.uiState] synchronously.
 *
 * Tests are expected to FAIL (red) until MOBILE-30 Item-2 migration removes the
 * [mainDispatcher] constructor parameter from [LoginViewModel] and replaces the manual
 * [kotlinx.coroutines.CoroutineScope] with [androidx.lifecycle.viewModelScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
        // ViewModel is constructed with no dispatcher param after the MOBILE-30 migration.
        viewModel = LoginViewModel(
            repository = fakeRepo,
            catalogRepository = FakeCatalogRepository(flagOn = false),
        )
    }

    // -------------------------------------------------------------------------
    // 1. Idle state on init
    // -------------------------------------------------------------------------

    @Test
    fun `idle state on init`() = runTest {
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
    fun `loading then success on sign-in`() = runTest {
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
    fun `loading then error on sign-in failure`() = runTest {
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
    fun `loading then error on user cancel`() = runTest {
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
    fun `second sign-in re-enters loading cycle`() = runTest {
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
    // CatalogRepository and exposes the result as `val isMockLayout: StateFlow<Boolean>`.
    // =========================================================================

    // -------------------------------------------------------------------------
    // 6. isMockLayout is false when catalog flag is OFF
    // -------------------------------------------------------------------------

    @Test
    fun `isMockLayout is false when catalog flag is off`() {
        val fakeCatalog = FakeCatalogRepository(flagOn = false)
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
        val fakeCatalog = FakeCatalogRepository(flagOn = true)
        val vm = LoginViewModel(
            repository = fakeRepo,
            catalogRepository = fakeCatalog,
        )

        assertTrue(
            "isMockLayout must be true when ${CatalogFlags.LOGIN_MOCK} is ON",
            vm.isMockLayout.value,
        )
    }

    // =========================================================================
    // Item-4 (MOBILE-30) — FakeCatalogRepository.isEnabled(id, default) contract
    //
    // When flagMissing=true the fake must return `default` (not hard-coded false).
    // This prevents Option-A tests from silently passing with the wrong value.
    // =========================================================================

    // -------------------------------------------------------------------------
    // 8. FakeCatalogRepository respects default when flag is missing
    // -------------------------------------------------------------------------

    @Test
    fun `FakeCatalogRepository returns default when flagMissing is true`() {
        val fakeMissing = FakeCatalogRepository(flagMissing = true)

        assertTrue(
            "isEnabled(id, default=true) must return true when flagMissing=true",
            fakeMissing.isEnabled(CatalogFlags.LOGIN_MOCK, default = true),
        )
        assertFalse(
            "isEnabled(id, default=false) must return false when flagMissing=true",
            fakeMissing.isEnabled(CatalogFlags.LOGIN_MOCK, default = false),
        )
    }

    // -------------------------------------------------------------------------
    // 9. FakeCatalogRepository.isEnabledFlow respects default when flag is missing
    // -------------------------------------------------------------------------

    @Test
    fun `FakeCatalogRepository isEnabledFlow returns default when flagMissing is true`() = runTest {
        val fakeMissing = FakeCatalogRepository(flagMissing = true)

        // isEnabledFlow is now backed by a (never-completing) StateFlow, so take the current value.
        val trueDefault =
            fakeMissing.isEnabledFlow(CatalogFlags.LOGIN_MOCK, default = true).first()
        assertTrue(
            "isEnabledFlow(id, default=true) must emit true when flagMissing=true, got $trueDefault",
            trueDefault,
        )

        val falseDefault =
            fakeMissing.isEnabledFlow(CatalogFlags.LOGIN_MOCK, default = false).first()
        assertFalse(
            "isEnabledFlow(id, default=false) must emit false when flagMissing=true, got $falseDefault",
            falseDefault,
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
 * [flagOn] — the explicit value to return when the flag is considered "present".
 * [flagMissing] — when true, behaves as if the flag is absent from the catalog:
 *   - [isEnabled] (single-arg) returns false (absent = OFF).
 *   - [isEnabled] (two-arg) returns [default] (Option-A: absent falls back to caller default).
 *   - [isEnabledFlow] emits [default] for the same reason.
 *
 * This mirrors the contract described in MOBILE-30 Item-4 so that Option-A tests
 * cannot accidentally pass by always returning false for a missing flag.
 */
private class FakeCatalogRepository(
    private val flagOn: Boolean = false,
    private val flagMissing: Boolean = false,
) : CatalogRepository {

    override suspend fun refresh() = Unit

    override fun isEnabled(id: Int): Boolean = if (flagMissing) false else flagOn

    override fun isEnabled(id: Int, default: Boolean): Boolean =
        if (flagMissing) default else flagOn

    /**
     * Backed by a [MutableStateFlow] (not [flowOf]) so it mirrors the real repository's hot
     * StateFlow: a collector stays subscribed and would observe a later [emitFlag] update.
     * This keeps the reactive update path (MOBILE-31) testable rather than completing after one emission.
     */
    private val flagFlow =
        kotlinx.coroutines.flow.MutableStateFlow(if (flagMissing) false else flagOn)

    override fun isEnabledFlow(id: Int, default: Boolean): kotlinx.coroutines.flow.Flow<Boolean> =
        if (flagMissing) kotlinx.coroutines.flow.MutableStateFlow(default) else flagFlow

    /** Drive a reactive update to subscribers of [isEnabledFlow] (present-flag case). */
    fun emitFlag(value: Boolean) {
        flagFlow.value = value
    }
}
