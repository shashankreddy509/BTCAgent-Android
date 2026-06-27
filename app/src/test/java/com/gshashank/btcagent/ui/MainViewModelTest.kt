package com.gshashank.btcagent.ui

import android.app.Activity
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseUser
import com.gshashank.btcagent.data.model.ColorTheme
import com.gshashank.btcagent.data.model.DashboardLayout
import com.gshashank.btcagent.data.repository.AppearanceRepository
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.ui.navigation.Route
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for [MainViewModel] — MOBILE-23 (rewrites the MOBILE-33 sync-val tests).
 *
 * [MainViewModel.startDestination] is now a [kotlinx.coroutines.flow.StateFlow]<[Route]?> resolved
 * asynchronously in an `init{}` coroutine that reads both the onboarding flag
 * ([AppearanceRepository.hasSeenOnboardingFlow]) and the Firebase auth session
 * ([AuthRepository.currentUser]).
 *
 * Priority rule (pinned here as the rollback contract):
 *   - hasSeenOnboarding == false  → [Route.Onboarding]  (regardless of session)
 *   - hasSeenOnboarding == true  + session present → [Route.Gate]
 *   - hasSeenOnboarding == true  + no session      → [Route.Login]
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so viewModelScope coroutines are driven synchronously
 * when [advanceUntilIdle] is called.
 *
 * All tests MUST fail (red) until the MOBILE-23 implementation is in place:
 *   - [Route.Onboarding] does not exist yet.
 *   - [AppearanceRepository] does not expose [hasSeenOnboardingFlow] / [setHasSeenOnboarding] yet.
 *   - [MainViewModel.startDestination] is currently a sync [Route] val, not [StateFlow]<[Route]?>.
 *   - [MainViewModel] does not accept [AppearanceRepository] yet.
 *   - [MainViewModel] does not expose [markOnboardingSeen] yet.
 *
 * Test coverage:
 *   1. notSeen + no session     → startDestination emits Route.Onboarding
 *   2. notSeen + session        → startDestination emits Route.Onboarding (onboarding takes priority)
 *   3. seen   + session         → startDestination emits Route.Gate
 *   4. seen   + no session      → startDestination emits Route.Login
 *   5. initialState_isNull      → startDestination.value is null immediately after construction
 *                                 (before init coroutine resolves with UnconfinedTestDispatcher the
 *                                  coroutine runs eagerly, so after advanceUntilIdle it is non-null)
 *   6. markOnboardingSeen       → delegates to AppearanceRepository.setHasSeenOnboarding(true)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun createViewModel(
        hasSeen: Boolean,
        user: FirebaseUser?,
    ): MainViewModel {
        val fakeAppearance = FakeAppearanceRepository(hasSeenOnboarding = hasSeen)
        val fakeAuth = FakeAuthRepository(user = user)
        return MainViewModel(
            authRepository = fakeAuth,
            appearanceRepository = fakeAppearance,
        )
    }

    // =========================================================================
    // 1. notSeen + no session → Route.Onboarding
    // =========================================================================

    @Test
    fun `notSeen_noSession_startsOnboarding`() = runTest {
        val vm = createViewModel(hasSeen = false, user = null)

        vm.startDestination.test {
            advanceUntilIdle()

            val destination = expectMostRecentItem()

            assertEquals(
                "hasSeenOnboarding=false + no session must route to Onboarding",
                Route.Onboarding,
                destination,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 2. notSeen + session → Route.Onboarding (onboarding priority over session)
    // =========================================================================

    @Test
    fun `notSeen_withSession_startsOnboarding`() = runTest {
        val vm = createViewModel(hasSeen = false, user = mock())

        vm.startDestination.test {
            advanceUntilIdle()

            val destination = expectMostRecentItem()

            assertEquals(
                "hasSeenOnboarding=false must route to Onboarding even when a session exists",
                Route.Onboarding,
                destination,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. seen + session → Route.Gate
    // =========================================================================

    @Test
    fun `seen_withSession_startsGate`() = runTest {
        val vm = createViewModel(hasSeen = true, user = mock())

        vm.startDestination.test {
            advanceUntilIdle()

            val destination = expectMostRecentItem()

            assertEquals(
                "hasSeenOnboarding=true + session present must route to Gate",
                Route.Gate,
                destination,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. seen + no session → Route.Login
    // =========================================================================

    @Test
    fun `seen_noSession_startsLogin`() = runTest {
        val vm = createViewModel(hasSeen = true, user = null)

        vm.startDestination.test {
            advanceUntilIdle()

            val destination = expectMostRecentItem()

            assertEquals(
                "hasSeenOnboarding=true + no session must route to Login",
                Route.Login,
                destination,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. initialState_isNull — StateFlow starts null before init coroutine resolves
    // =========================================================================

    @Test
    fun `startDestination is non-null after init coroutine resolves`() = runTest {
        // Uses MainDispatcherRule (UnconfinedTestDispatcher) so the init{} coroutine is
        // driven by advanceUntilIdle. The key contract: after resolution the StateFlow
        // always holds a concrete Route (never stays null).
        val vm = createViewModel(hasSeen = false, user = null)
        advanceUntilIdle()

        assertNotNull(
            "startDestination must be non-null after the init coroutine has resolved",
            vm.startDestination.value,
        )
    }

    // =========================================================================
    // 6. markOnboardingSeen delegates to AppearanceRepository.setHasSeenOnboarding(true)
    // =========================================================================

    @Test
    fun `markOnboardingSeen calls AppearanceRepository setHasSeenOnboarding with true`() =
        runTest {
            val fakeAppearance = FakeAppearanceRepository(hasSeenOnboarding = false)
            val fakeAuth = FakeAuthRepository(user = null)
            val vm = MainViewModel(
                authRepository = fakeAuth,
                appearanceRepository = fakeAppearance,
            )
            advanceUntilIdle()

            vm.markOnboardingSeen()
            advanceUntilIdle()

            assertEquals(
                "markOnboardingSeen must call AppearanceRepository.setHasSeenOnboarding(true) exactly once",
                1,
                fakeAppearance.setHasSeenOnboardingCallCount,
            )
            assertEquals(
                "markOnboardingSeen must pass true to setHasSeenOnboarding",
                true,
                fakeAppearance.lastHasSeenOnboardingValue,
            )
        }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [AppearanceRepository] for MainViewModel tests — MOBILE-23.
 *
 * Exposes a [MutableStateFlow]-backed [hasSeenOnboardingFlow] whose initial value is set via
 * the constructor. Call-count and last-value properties let tests assert invocation details for
 * [setHasSeenOnboarding].
 *
 * All other appearance flows return inert defaults — [MainViewModel] does not read them.
 */
private class FakeAppearanceRepository(
    hasSeenOnboarding: Boolean = false,
) : AppearanceRepository {

    private val _hasSeenOnboarding = MutableStateFlow(hasSeenOnboarding)

    override val hasSeenOnboardingFlow: Flow<Boolean> = _hasSeenOnboarding

    var setHasSeenOnboardingCallCount: Int = 0
    var lastHasSeenOnboardingValue: Boolean? = null

    override suspend fun setHasSeenOnboarding(seen: Boolean) {
        setHasSeenOnboardingCallCount++
        lastHasSeenOnboardingValue = seen
        _hasSeenOnboarding.value = seen
    }

    // ---- stubs for other AppearanceRepository members (not used by MainViewModel) ----

    override val darkModeFlow: Flow<Boolean> = MutableStateFlow(false)
    override val colorThemeFlow: Flow<ColorTheme> = MutableStateFlow(ColorTheme.BITCOIN)
    override val dashboardLayoutFlow: Flow<DashboardLayout> = MutableStateFlow(DashboardLayout.HERO)
    override val biometricUnlockFlow: Flow<Boolean> = MutableStateFlow(false)

    override suspend fun setDarkMode(enabled: Boolean) = Unit
    override suspend fun setColorTheme(theme: ColorTheme) = Unit
    override suspend fun setDashboardLayout(layout: DashboardLayout) = Unit
    override suspend fun setBiometricUnlock(enabled: Boolean) = Unit
}

/**
 * Hand-written fake [AuthRepository] for MainViewModel tests.
 *
 * Only [currentUser] is read by [MainViewModel]; all other methods are unused stubs.
 */
private class FakeAuthRepository(private val user: FirebaseUser?) : AuthRepository {

    override val currentUser: FirebaseUser? = user

    override suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> =
        Result.failure(UnsupportedOperationException("not needed in MainViewModel tests"))

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("not needed in MainViewModel tests"))

    override fun signOut() = Unit
}
