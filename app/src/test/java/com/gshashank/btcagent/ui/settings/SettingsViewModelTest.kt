package com.gshashank.btcagent.ui.settings

import android.app.Activity
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseUser
import com.gshashank.btcagent.data.model.ColorTheme
import com.gshashank.btcagent.data.model.DashboardLayout
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.UserSettings
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.AppearanceRepository
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.data.repository.SettingsRepository
import com.gshashank.btcagent.data.repository.SettingsResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [SettingsViewModel] — MOBILE-20.
 *
 * Uses hand-written fakes for all collaborators ([FakeSettingsRepository],
 * [FakeAppearanceRepository], [FakeAuthRepository]) so no real network or Android framework
 * calls are made. [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * as [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * **No catalog flag** — Settings is foundational (user decision). No catalog-gating test needed.
 *
 * All tests MUST fail (red) until [SettingsViewModel] is implemented.
 *
 * Test coverage:
 *   1.  Initial state is UiState.Loading before fetch completes
 *   2.  Load success → uiState = Ready with correct UserSettings
 *   3.  Load failure → uiState = Error
 *   4.  saveTradingParams success → ActionResultUiState.Success + triggers refresh
 *   5.  qty=0 → validation error, no PUT, actionResult shows Error
 *   6.  qty=3 (odd) → validation error, no PUT
 *   7.  Dark mode toggle → calls AppearanceRepository.setDarkMode
 *   8.  Sign out → calls AuthRepository.signOut() + navigateToLogin emits
 *   9.  Double-tap guard on save: second tap while in-flight is ignored
 *   10. Masked broker key is present read-only in uiState (never sent back via saveTradingParams)
 *   11. setColorTheme(COBALT) → calls AppearanceRepository.setColorTheme with ColorTheme.COBALT
 *   12. setColorTheme(VIOLET) → calls AppearanceRepository.setColorTheme with ColorTheme.VIOLET
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeSettingsRepo: FakeSettingsRepository
    private lateinit var fakeAppearanceRepo: FakeAppearanceRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository

    // -------------------------------------------------------------------------
    // Stable domain fixtures
    // -------------------------------------------------------------------------

    private val sampleSettings = UserSettings(
        qty = 4,
        maxSl = 2.5,
        minTp = 1.0,
        maxConcurrent = 3,
        mode = ExecutionMode.PAPER,
        brokerKeys = listOf("ABCD****WXYZ"),
    )

    @Before
    fun setUp() {
        fakeSettingsRepo = FakeSettingsRepository()
        fakeAppearanceRepo = FakeAppearanceRepository()
        fakeAuthRepo = FakeAuthRepository()
    }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            settingsRepository = fakeSettingsRepo,
            appearanceRepository = fakeAppearanceRepo,
            authRepository = fakeAuthRepo,
        )

    // =========================================================================
    // 1. Initial state is UiState.Loading before fetch completes
    // =========================================================================

    @Test
    fun `initial state is Loading before first fetch completes`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

        val viewModel = createViewModel()

        // Before any coroutines run, state must be Loading
        assertEquals(
            "uiState must be Loading immediately after construction before coroutines run",
            UiState.Loading,
            viewModel.uiState.value,
        )
    }

    // =========================================================================
    // 2. Load success → uiState = Ready with correct UserSettings
    // =========================================================================

    @Test
    fun `load success transitions uiState to Ready with correct UserSettings`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue(
                "uiState must be UiState.Ready when repo returns SettingsResult.Success, got $ready",
                ready is UiState.Ready<*>,
            )

            @Suppress("UNCHECKED_CAST")
            val data = (ready as UiState.Ready<UserSettings>).data

            assertEquals(
                "qty must be populated from fetched settings",
                4,
                data.qty,
            )
            assertEquals(
                "maxSl must be populated from fetched settings",
                2.5,
                data.maxSl ?: 0.0,
                0.001,
            )
            assertEquals(
                "mode must be populated from fetched settings",
                ExecutionMode.PAPER,
                data.mode,
            )
            assertTrue(
                "brokerKeys must be non-empty from fetched settings",
                data.brokerKeys.isNotEmpty(),
            )
            assertEquals(
                "Masked broker key must be present as a display string",
                "ABCD****WXYZ",
                data.brokerKeys[0],
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Load failure → uiState = Error
    // =========================================================================

    @Test
    fun `load failure transitions uiState to Error`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Error(message = "Network unavailable")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(
                "uiState must be UiState.Error when repo returns SettingsResult.Error, got $error",
                error is UiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. saveTradingParams success → ActionResultUiState.Success + triggers refresh
    // =========================================================================

    @Test
    fun `saveTradingParams success emits ActionResultUiState Success and triggers refresh`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
            fakeSettingsRepo.saveResult = ActionResult.Success

            val viewModel = createViewModel()
            advanceUntilIdle() // allow initial fetch to complete

            viewModel.actionResult.test {
                awaitItem() // consume initial null

                viewModel.saveTradingParams(
                    qty = 4,
                    maxSl = 2.5,
                    minTp = 1.0,
                    maxConcurrent = 3,
                    mode = ExecutionMode.PAPER,
                )
                advanceUntilIdle()

                val result = awaitItem()
                assertTrue(
                    "actionResult must be ActionResultUiState.Success after saveTradingParams succeeds, got $result",
                    result is ActionResultUiState.Success,
                )

                // Refresh: fetchUserSettings must have been called more than once
                assertTrue(
                    "fetchUserSettings must be called again after save succeeds (at least 2 total calls)",
                    fakeSettingsRepo.fetchCallCount >= 2,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 5. qty=0 → validation error, no PUT, actionResult shows Error
    // =========================================================================

    @Test
    fun `saveTradingParams with qty=0 shows validation error and does not call repository`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
            fakeSettingsRepo.saveResult = ActionResult.Success

            val viewModel = createViewModel()
            advanceUntilIdle()

            val saveCallsBefore = fakeSettingsRepo.saveCallCount

            viewModel.saveTradingParams(
                qty = 0,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )
            advanceUntilIdle()

            assertEquals(
                "qty=0 validation must block the PUT — saveCallCount must not increase",
                saveCallsBefore,
                fakeSettingsRepo.saveCallCount,
            )

            // The ViewModel must surface the validation error to the UI via actionResult
            val actionResult = viewModel.actionResult.value
            assertTrue(
                "actionResult must indicate an error when qty=0 validation fails, got $actionResult",
                actionResult is ActionResultUiState.Error,
            )
        }

    // =========================================================================
    // 6. qty=3 (odd) → validation error, no PUT
    // =========================================================================

    @Test
    fun `saveTradingParams with odd qty=3 shows validation error and does not call repository`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
            fakeSettingsRepo.saveResult = ActionResult.Success

            val viewModel = createViewModel()
            advanceUntilIdle()

            val saveCallsBefore = fakeSettingsRepo.saveCallCount

            viewModel.saveTradingParams(
                qty = 3,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )
            advanceUntilIdle()

            assertEquals(
                "qty=3 (odd) validation must block the PUT — saveCallCount must not increase",
                saveCallsBefore,
                fakeSettingsRepo.saveCallCount,
            )

            val actionResult = viewModel.actionResult.value
            assertTrue(
                "actionResult must indicate an error when odd qty=3 validation fails, got $actionResult",
                actionResult is ActionResultUiState.Error,
            )
        }

    // =========================================================================
    // 7. Dark mode toggle → calls AppearanceRepository.setDarkMode
    // =========================================================================

    @Test
    fun `setDarkMode true calls AppearanceRepository setDarkMode with true`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val setDarkModeCallsBefore = fakeAppearanceRepo.setDarkModeCallCount

        viewModel.setDarkMode(true)
        advanceUntilIdle()

        assertEquals(
            "setDarkMode(true) must call AppearanceRepository.setDarkMode exactly once",
            setDarkModeCallsBefore + 1,
            fakeAppearanceRepo.setDarkModeCallCount,
        )
        assertEquals(
            "AppearanceRepository.setDarkMode must be called with value=true",
            true,
            fakeAppearanceRepo.lastDarkModeValue,
        )
    }

    @Test
    fun `setDarkMode false calls AppearanceRepository setDarkMode with false`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDarkMode(false)
        advanceUntilIdle()

        assertEquals(
            "AppearanceRepository.setDarkMode must be called with value=false",
            false,
            fakeAppearanceRepo.lastDarkModeValue,
        )
    }

    // =========================================================================
    // 8. Sign out → calls AuthRepository.signOut() + navigateToLogin emits
    // =========================================================================

    @Test
    fun `signOut calls AuthRepository signOut`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val signOutCallsBefore = fakeAuthRepo.signOutCallCount

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(
            "signOut must call AuthRepository.signOut() exactly once",
            signOutCallsBefore + 1,
            fakeAuthRepo.signOutCallCount,
        )
    }

    @Test
    fun `signOut emits navigateToLogin event after calling AuthRepository signOut`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.navigateToLogin.test {
            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(
                "signOut must call AuthRepository.signOut() exactly once",
                1,
                fakeAuthRepo.signOutCallCount,
            )

            // Consume the navigation event — any emission proves the event was fired
            awaitItem()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 9. Double-tap guard on save: second tap while in-flight is ignored
    // =========================================================================

    @Test
    fun `second saveTradingParams while first is in-flight is ignored (double-tap guard)`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
            fakeSettingsRepo.saveResult = ActionResult.Success

            val viewModel = createViewModel()
            advanceUntilIdle()

            val saveCallsBefore = fakeSettingsRepo.saveCallCount

            // Fire two consecutive calls without letting them complete between calls
            viewModel.saveTradingParams(
                qty = 4,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )
            viewModel.saveTradingParams(
                qty = 4,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )
            advanceUntilIdle()

            assertEquals(
                "Double-tap guard: saveTradingParams must only be forwarded to the repo once, not twice",
                saveCallsBefore + 1,
                fakeSettingsRepo.saveCallCount,
            )
        }

    // =========================================================================
    // 10. Masked broker key is present read-only in uiState
    //     (never sent back via saveTradingParams signature)
    // =========================================================================

    @Test
    fun `masked broker key appears in uiState Ready data as read-only display string`() = runTest {
        val settingsWithMaskedKey = UserSettings(
            qty = 4,
            maxSl = 2.5,
            minTp = 1.0,
            maxConcurrent = 3,
            mode = ExecutionMode.PAPER,
            brokerKeys = listOf("ABCD****WXYZ"),
        )
        fakeSettingsRepo.fetchResult = SettingsResult.Success(settingsWithMaskedKey)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(
            "uiState must be Ready after fetch with masked broker key, got $uiState",
            uiState is UiState.Ready<*>,
        )

        @Suppress("UNCHECKED_CAST")
        val data = (uiState as UiState.Ready<UserSettings>).data

        assertTrue(
            "brokerKeys must be present in the loaded state (read-only display)",
            data.brokerKeys.isNotEmpty(),
        )
        assertTrue(
            "The masked key must contain '****' (masked sentinel — indicates read-only display value)",
            data.brokerKeys[0].contains("****"),
        )
    }

    @Test
    fun `saveTradingParams does not include masked sentinel in params passed to repository`() =
        runTest {
            // The ViewModel MUST NOT forward masked broker-key strings through saveTradingParams.
            // saveTradingParams only accepts trading params (qty, maxSl, minTp, maxConcurrent, mode);
            // broker keys are read-only and never appear as saveTradingParams arguments.
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
            fakeSettingsRepo.saveResult = ActionResult.Success

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.saveTradingParams(
                qty = 4,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )
            advanceUntilIdle()

            val args = fakeSettingsRepo.lastSaveArgs
            assertNotNull("saveTradingParams must have been called", args)

            // Verify no "****" sentinel leaks through any saveTradingParams argument
            val modeStr = args!!.mode?.name ?: ""
            assertFalse(
                "mode parameter must not contain '****' — masked values must never be forwarded to the repo",
                modeStr.contains("****"),
            )
        }

    // =========================================================================
    // 11. setColorTheme(COBALT) → calls AppearanceRepository.setColorTheme(COBALT)
    //     and colorTheme StateFlow reflects COBALT — MOBILE-25
    // =========================================================================

    @Test
    fun `setColorTheme COBALT calls AppearanceRepository setColorTheme with ColorTheme COBALT`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val setColorThemeCallsBefore = fakeAppearanceRepo.setColorThemeCallCount

            viewModel.setColorTheme(ColorTheme.COBALT)
            advanceUntilIdle()

            assertEquals(
                "setColorTheme(COBALT) must call AppearanceRepository.setColorTheme exactly once",
                setColorThemeCallsBefore + 1,
                fakeAppearanceRepo.setColorThemeCallCount,
            )
            assertEquals(
                "AppearanceRepository.setColorTheme must be called with ColorTheme.COBALT",
                ColorTheme.COBALT,
                fakeAppearanceRepo.lastColorThemeValue,
            )
            assertEquals(
                "colorTheme StateFlow must emit ColorTheme.COBALT after setColorTheme(COBALT)",
                ColorTheme.COBALT,
                viewModel.colorTheme.value,
            )
        }

    // =========================================================================
    // 12. setColorTheme(VIOLET) → calls AppearanceRepository.setColorTheme(VIOLET)
    //     and colorTheme StateFlow reflects VIOLET — MOBILE-25
    // =========================================================================

    @Test
    fun `setColorTheme VIOLET calls AppearanceRepository setColorTheme with ColorTheme VIOLET`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val setColorThemeCallsBefore = fakeAppearanceRepo.setColorThemeCallCount

            viewModel.setColorTheme(ColorTheme.VIOLET)
            advanceUntilIdle()

            assertEquals(
                "setColorTheme(VIOLET) must call AppearanceRepository.setColorTheme exactly once",
                setColorThemeCallsBefore + 1,
                fakeAppearanceRepo.setColorThemeCallCount,
            )
            assertEquals(
                "AppearanceRepository.setColorTheme must be called with ColorTheme.VIOLET",
                ColorTheme.VIOLET,
                fakeAppearanceRepo.lastColorThemeValue,
            )
            assertEquals(
                "colorTheme StateFlow must emit ColorTheme.VIOLET after setColorTheme(VIOLET)",
                ColorTheme.VIOLET,
                viewModel.colorTheme.value,
            )
        }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [SettingsRepository].
 * Configure result properties before each test to control behaviour.
 * Call counts and captured args let tests assert invocation details.
 */
private class FakeSettingsRepository : SettingsRepository {

    var fetchResult: SettingsResult =
        SettingsResult.Error(message = "fetchResult not configured")
    var saveResult: ActionResult = ActionResult.Success

    var fetchCallCount: Int = 0
    var saveCallCount: Int = 0

    /** Captures arguments from the last saveTradingParams call for inspection. */
    var lastSaveArgs: SaveArgs? = null

    data class SaveArgs(
        val qty: Int?,
        val maxSl: Double?,
        val minTp: Double?,
        val maxConcurrent: Int?,
        val mode: ExecutionMode?,
    )

    override suspend fun fetchUserSettings(): SettingsResult {
        fetchCallCount++
        return fetchResult
    }

    override suspend fun saveTradingParams(
        qty: Int?,
        maxSl: Double?,
        minTp: Double?,
        maxConcurrent: Int?,
        mode: ExecutionMode?,
    ): ActionResult {
        saveCallCount++
        lastSaveArgs = SaveArgs(
            qty = qty,
            maxSl = maxSl,
            minTp = minTp,
            maxConcurrent = maxConcurrent,
            mode = mode,
        )
        return saveResult
    }
}

/**
 * Hand-written fake [AppearanceRepository].
 * Backed by [MutableStateFlow]s so Flow-based tests can observe reactive updates.
 *
 * Extended in MOBILE-25 to track [lastColorThemeValue] — the value argument passed to the most
 * recent [setColorTheme] call. Tests assert referential identity to confirm the correct enum
 * constant was forwarded.
 */
private class FakeAppearanceRepository : AppearanceRepository {

    private val _darkMode = MutableStateFlow(false)
    private val _colorTheme = MutableStateFlow(ColorTheme.BITCOIN)
    private val _dashboardLayout = MutableStateFlow(DashboardLayout.HERO)
    private val _biometricUnlock = MutableStateFlow(false)

    override val darkModeFlow: Flow<Boolean> = _darkMode
    override val colorThemeFlow: Flow<ColorTheme> = _colorTheme
    override val dashboardLayoutFlow: Flow<DashboardLayout> = _dashboardLayout
    override val biometricUnlockFlow: Flow<Boolean> = _biometricUnlock
    override val hasSeenOnboardingFlow: Flow<Boolean> = MutableStateFlow(false)

    var setDarkModeCallCount: Int = 0
    var lastDarkModeValue: Boolean? = null

    var setColorThemeCallCount: Int = 0
    /** The [ColorTheme] value passed to the most recent [setColorTheme] call. Null if never called. */
    var lastColorThemeValue: ColorTheme? = null

    var setDashboardLayoutCallCount: Int = 0
    var setBiometricUnlockCallCount: Int = 0

    override suspend fun setDarkMode(enabled: Boolean) {
        setDarkModeCallCount++
        lastDarkModeValue = enabled
        _darkMode.value = enabled
    }

    override suspend fun setColorTheme(theme: ColorTheme) {
        setColorThemeCallCount++
        lastColorThemeValue = theme
        _colorTheme.value = theme
    }

    override suspend fun setDashboardLayout(layout: DashboardLayout) {
        setDashboardLayoutCallCount++
        _dashboardLayout.value = layout
    }

    override suspend fun setBiometricUnlock(enabled: Boolean) {
        setBiometricUnlockCallCount++
        _biometricUnlock.value = enabled
    }

    override suspend fun setHasSeenOnboarding(seen: Boolean) = Unit
}

/**
 * Hand-written fake [AuthRepository] for SettingsViewModel tests.
 * Only [signOut] is exercised here; other methods are stubs.
 */
private class FakeAuthRepository : AuthRepository {

    var signOutCallCount: Int = 0

    override val currentUser: FirebaseUser? = null

    override suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> =
        Result.failure(UnsupportedOperationException("not needed in SettingsViewModel tests"))

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("not needed in SettingsViewModel tests"))

    override fun signOut() {
        signOutCallCount++
    }
}
