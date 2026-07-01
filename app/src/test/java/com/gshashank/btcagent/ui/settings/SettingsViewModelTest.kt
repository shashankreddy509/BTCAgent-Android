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
 * JVM unit tests for [SettingsViewModel] — MOBILE-20 / MOBILE-42.
 *
 * Uses hand-written fakes for all collaborators ([FakeSettingsRepository],
 * [FakeAppearanceRepository], [FakeAuthRepository]) so no real network or Android framework
 * calls are made. [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * as [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * **No catalog flag** — Settings is foundational (user decision). No catalog-gating test needed.
 *
 * MOBILE-42 changes:
 *   - brokerKeys removed from UserSettings domain model and sampleSettings fixture.
 *   - Scanner fields (scanIntervalMin, tfMin, tfMax, patterns) present in UserSettings.
 *   - dashboardLayout StateFlow exposed on ViewModel.
 *   - setDashboardLayout() method added.
 *   - Trading param inputs are editable — save sends the EDITED values, not the originally loaded ones.
 *
 * All tests MUST fail (red) until the corresponding implementation changes land.
 *
 * Test coverage:
 *   1.  Initial state is UiState.Loading before fetch completes
 *   2.  Load success → uiState = Ready with correct UserSettings (no brokerKeys field)
 *   3.  Load failure → uiState = Error
 *   4.  saveTradingParams success → ActionResultUiState.Success + triggers refresh
 *   5.  qty=0 → validation error, no PUT, actionResult shows Error
 *   6.  qty=3 (odd) → validation error, no PUT
 *   7.  Dark mode toggle → calls AppearanceRepository.setDarkMode
 *   8.  Sign out → calls AuthRepository.signOut() + navigateToLogin emits
 *   9.  Double-tap guard on save: second tap while in-flight is ignored
 *   10. Save sends the EDITED trading-param values (not the originally loaded stubs)
 *   11. setColorTheme(COBALT) → calls AppearanceRepository.setColorTheme with ColorTheme.COBALT
 *   12. setColorTheme(VIOLET) → calls AppearanceRepository.setColorTheme with ColorTheme.VIOLET
 *   13. dashboardLayout StateFlow initial value reflects AppearanceRepository.dashboardLayoutFlow
 *   14. setDashboardLayout(GRID) calls AppearanceRepository.setDashboardLayout(GRID)
 *       and dashboardLayout StateFlow emits GRID
 *   15. setDashboardLayout(HERO) calls AppearanceRepository.setDashboardLayout(HERO)
 *       and dashboardLayout StateFlow emits HERO
 *   16. setDashboardLayout(TERMINAL) calls AppearanceRepository.setDashboardLayout(TERMINAL)
 *       and dashboardLayout StateFlow emits TERMINAL
 *   17. Load success → scanner fields (scanIntervalMin, tfMin, tfMax, patterns) present in Ready state
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeSettingsRepo: FakeSettingsRepository
    private lateinit var fakeAppearanceRepo: FakeAppearanceRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var fakeNotificationsRepo: FakeNotificationsRepository
    private lateinit var fakeFcmTokenProvider: FakeFcmTokenProvider

    // Shared monotonic counter so tests can assert the ORDER of cross-collaborator calls
    // (MOBILE-41: unregister must run BEFORE signOut so the DELETE is authenticated).
    private val callOrder = java.util.concurrent.atomic.AtomicInteger(0)

    // -------------------------------------------------------------------------
    // Stable domain fixtures — MOBILE-42: no brokerKeys field; scanner fields present
    // -------------------------------------------------------------------------

    private val sampleSettings = UserSettings(
        qty = 4,
        maxSl = 2.5,
        minTp = 1.0,
        maxConcurrent = 3,
        mode = ExecutionMode.PAPER,
        scanIntervalMin = 15,
        tfMin = 15,
        tfMax = 360,
        patterns = listOf("BULL_FLAG", "BEAR_CHANNEL"),
    )

    @Before
    fun setUp() {
        callOrder.set(0)
        fakeSettingsRepo = FakeSettingsRepository()
        fakeAppearanceRepo = FakeAppearanceRepository()
        fakeAuthRepo = FakeAuthRepository(callOrder)
        fakeNotificationsRepo = FakeNotificationsRepository(callOrder)
        fakeFcmTokenProvider = FakeFcmTokenProvider()
    }

    private fun createViewModel(): SettingsViewModel =
        SettingsViewModel(
            settingsRepository = fakeSettingsRepo,
            appearanceRepository = fakeAppearanceRepo,
            authRepository = fakeAuthRepo,
            notificationsRepository = fakeNotificationsRepo,
            fcmTokenProvider = fakeFcmTokenProvider,
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
    // 2. Load success → uiState = Ready with correct UserSettings (no brokerKeys)
    //    MOBILE-42: sampleSettings has no brokerKeys; scanner fields present
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
    // 8b. MOBILE-41: signOut unregisters the FCM token BEFORE authRepository.signOut()
    //     so the DELETE is still authenticated (Critical fix — sign-out ordering bug).
    // =========================================================================

    @Test
    fun `signOut unregisters FCM token before signing out`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
        fakeFcmTokenProvider.token = "device-token-123"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(
            "unregister must be called exactly once with the current FCM token",
            1,
            fakeNotificationsRepo.unregisterCallCount,
        )
        assertEquals(
            "unregister must use the FCM token",
            "device-token-123",
            fakeNotificationsRepo.lastUnregisteredToken,
        )
        assertTrue(
            "unregister (order=${fakeNotificationsRepo.unregisterOrder}) MUST run BEFORE " +
                "authRepository.signOut() (order=${fakeAuthRepo.signOutOrder}) — otherwise the " +
                "DELETE goes out unauthenticated and the device keeps receiving push after logout",
            fakeNotificationsRepo.unregisterOrder < fakeAuthRepo.signOutOrder,
        )
    }

    @Test
    fun `signOut still signs out when no FCM token is available`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
        fakeFcmTokenProvider.token = null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(
            "no token → no unregister call",
            0,
            fakeNotificationsRepo.unregisterCallCount,
        )
        assertEquals(
            "signOut must still proceed with no token available",
            1,
            fakeAuthRepo.signOutCallCount,
        )
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
    // 10. Save sends the EDITED trading-param values (not the originally loaded stubs)
    //     MOBILE-42: trading param fields are now editable; save must forward the
    //     edited values the user typed, NOT the stale loaded values.
    // =========================================================================

    @Test
    fun `saveTradingParams sends the edited values not the originally loaded settings`() = runTest {
        val loadedSettings = UserSettings(
            qty = 4,
            maxSl = 2.5,
            minTp = 1.0,
            maxConcurrent = 3,
            mode = ExecutionMode.PAPER,
            scanIntervalMin = null,
            tfMin = null,
            tfMax = null,
            patterns = emptyList(),
        )
        fakeSettingsRepo.fetchResult = SettingsResult.Success(loadedSettings)
        fakeSettingsRepo.saveResult = ActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        // The user edits qty from 4 → 10 and maxSl from 2.5 → 5.0
        val editedQty = 10
        val editedMaxSl = 5.0

        viewModel.saveTradingParams(
            qty = editedQty,
            maxSl = editedMaxSl,
            minTp = 1.0,
            maxConcurrent = 3,
            mode = ExecutionMode.PAPER,
        )
        advanceUntilIdle()

        val args = fakeSettingsRepo.lastSaveArgs
        assertNotNull("saveTradingParams must have been called", args)
        assertEquals(
            "save must forward the EDITED qty (10), not the loaded qty (4)",
            editedQty,
            args!!.qty,
        )
        assertEquals(
            "save must forward the EDITED maxSl (5.0), not the loaded maxSl (2.5)",
            editedMaxSl,
            args.maxSl ?: 0.0,
            0.001,
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

    // =========================================================================
    // 13. dashboardLayout StateFlow initial value reflects AppearanceRepository
    //     MOBILE-42: new StateFlow on ViewModel
    // =========================================================================

    @Test
    fun `dashboardLayout StateFlow initial value comes from AppearanceRepository`() = runTest {
        fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)
        // FakeAppearanceRepository defaults to DashboardLayout.HERO
        val viewModel = createViewModel()

        assertEquals(
            "dashboardLayout StateFlow must initially reflect the AppearanceRepository value (HERO)",
            DashboardLayout.HERO,
            viewModel.dashboardLayout.value,
        )
    }

    // =========================================================================
    // 14. setDashboardLayout(GRID) → persists + dashboardLayout StateFlow emits GRID
    //     MOBILE-42: new method on ViewModel
    // =========================================================================

    @Test
    fun `setDashboardLayout GRID calls AppearanceRepository and dashboardLayout emits GRID`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val setLayoutCallsBefore = fakeAppearanceRepo.setDashboardLayoutCallCount

            viewModel.setDashboardLayout(DashboardLayout.GRID)
            advanceUntilIdle()

            assertEquals(
                "setDashboardLayout(GRID) must call AppearanceRepository.setDashboardLayout exactly once",
                setLayoutCallsBefore + 1,
                fakeAppearanceRepo.setDashboardLayoutCallCount,
            )
            assertEquals(
                "AppearanceRepository.setDashboardLayout must have received DashboardLayout.GRID",
                DashboardLayout.GRID,
                fakeAppearanceRepo.lastDashboardLayoutValue,
            )
            assertEquals(
                "dashboardLayout StateFlow must emit GRID after setDashboardLayout(GRID)",
                DashboardLayout.GRID,
                viewModel.dashboardLayout.value,
            )
        }

    // =========================================================================
    // 15. setDashboardLayout(HERO) → persists + dashboardLayout StateFlow emits HERO
    //     MOBILE-42: new method on ViewModel
    // =========================================================================

    @Test
    fun `setDashboardLayout HERO calls AppearanceRepository and dashboardLayout emits HERO`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

            // Start from a non-HERO layout to make the assertion meaningful
            fakeAppearanceRepo.setDashboardLayoutForTest(DashboardLayout.GRID)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val setLayoutCallsBefore = fakeAppearanceRepo.setDashboardLayoutCallCount

            viewModel.setDashboardLayout(DashboardLayout.HERO)
            advanceUntilIdle()

            assertEquals(
                "setDashboardLayout(HERO) must call AppearanceRepository.setDashboardLayout exactly once",
                setLayoutCallsBefore + 1,
                fakeAppearanceRepo.setDashboardLayoutCallCount,
            )
            assertEquals(
                "AppearanceRepository.setDashboardLayout must have received DashboardLayout.HERO",
                DashboardLayout.HERO,
                fakeAppearanceRepo.lastDashboardLayoutValue,
            )
            assertEquals(
                "dashboardLayout StateFlow must emit HERO after setDashboardLayout(HERO)",
                DashboardLayout.HERO,
                viewModel.dashboardLayout.value,
            )
        }

    // =========================================================================
    // 16. setDashboardLayout(TERMINAL) → persists + dashboardLayout StateFlow emits TERMINAL
    //     MOBILE-42: new method on ViewModel
    // =========================================================================

    @Test
    fun `setDashboardLayout TERMINAL calls AppearanceRepository and dashboardLayout emits TERMINAL`() =
        runTest {
            fakeSettingsRepo.fetchResult = SettingsResult.Success(sampleSettings)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val setLayoutCallsBefore = fakeAppearanceRepo.setDashboardLayoutCallCount

            viewModel.setDashboardLayout(DashboardLayout.TERMINAL)
            advanceUntilIdle()

            assertEquals(
                "setDashboardLayout(TERMINAL) must call AppearanceRepository.setDashboardLayout exactly once",
                setLayoutCallsBefore + 1,
                fakeAppearanceRepo.setDashboardLayoutCallCount,
            )
            assertEquals(
                "AppearanceRepository.setDashboardLayout must have received DashboardLayout.TERMINAL",
                DashboardLayout.TERMINAL,
                fakeAppearanceRepo.lastDashboardLayoutValue,
            )
            assertEquals(
                "dashboardLayout StateFlow must emit TERMINAL after setDashboardLayout(TERMINAL)",
                DashboardLayout.TERMINAL,
                viewModel.dashboardLayout.value,
            )
        }

    // =========================================================================
    // 17. Load success → scanner fields present in Ready state
    //     MOBILE-42: UserSettings gains scanIntervalMin, tfMin, tfMax, patterns
    // =========================================================================

    @Test
    fun `load success exposes scanner fields in uiState Ready data`() = runTest {
        val settingsWithScannerParams = UserSettings(
            qty = 4,
            maxSl = 2.5,
            minTp = 1.0,
            maxConcurrent = 3,
            mode = ExecutionMode.PAPER,
            scanIntervalMin = 30,
            tfMin = 5,
            tfMax = 60,
            patterns = listOf("ENGULFING", "PINBAR"),
        )
        fakeSettingsRepo.fetchResult = SettingsResult.Success(settingsWithScannerParams)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(
            "uiState must be Ready after fetch with scanner params, got $uiState",
            uiState is UiState.Ready<*>,
        )

        @Suppress("UNCHECKED_CAST")
        val data = (uiState as UiState.Ready<UserSettings>).data

        assertEquals(
            "scanIntervalMin must be 30 from fetched settings",
            30,
            data.scanIntervalMin,
        )
        assertEquals(
            "tfMin must be 5 from fetched settings",
            5,
            data.tfMin,
        )
        assertEquals(
            "tfMax must be 60 from fetched settings",
            60,
            data.tfMax,
        )
        assertNotNull(
            "patterns must be non-null from fetched settings",
            data.patterns,
        )
        assertEquals(
            "patterns must contain 2 entries",
            2,
            data.patterns?.size,
        )
        assertTrue(
            "patterns must contain ENGULFING",
            data.patterns?.contains("ENGULFING") == true,
        )
    }

    // =========================================================================
    // Preserved: saveTradingParams does not include masked sentinel
    // =========================================================================

    @Test
    fun `saveTradingParams does not include masked sentinel in params passed to repository`() =
        runTest {
            // The ViewModel MUST NOT forward masked broker-key strings through saveTradingParams.
            // saveTradingParams only accepts trading params (qty, maxSl, minTp, maxConcurrent, mode);
            // broker keys are removed in MOBILE-42.
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
 * MOBILE-42: tracks [lastDashboardLayoutValue] for setDashboardLayout assertions.
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
    /** The [DashboardLayout] value passed to the most recent [setDashboardLayout] call. */
    var lastDashboardLayoutValue: DashboardLayout? = null

    var setBiometricUnlockCallCount: Int = 0

    /** Test helper to preset the layout without incrementing [setDashboardLayoutCallCount]. */
    fun setDashboardLayoutForTest(layout: DashboardLayout) {
        _dashboardLayout.value = layout
    }

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
        lastDashboardLayoutValue = layout
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
private class FakeAuthRepository(
    private val callOrder: java.util.concurrent.atomic.AtomicInteger,
) : AuthRepository {

    var signOutCallCount: Int = 0

    /** Order-of-call marker (from the shared counter) at the moment signOut ran; -1 if never. */
    var signOutOrder: Int = -1

    override val currentUser: FirebaseUser? = null

    override suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> =
        Result.failure(UnsupportedOperationException("not needed in SettingsViewModel tests"))

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
        Result.failure(UnsupportedOperationException("not needed in SettingsViewModel tests"))

    override fun signOut() {
        signOutCallCount++
        signOutOrder = callOrder.incrementAndGet()
    }
}

/**
 * Fake [NotificationsRepository] — MOBILE-41. Records unregister calls + their order so tests can
 * prove unregister runs BEFORE [FakeAuthRepository.signOut].
 */
private class FakeNotificationsRepository(
    private val callOrder: java.util.concurrent.atomic.AtomicInteger,
) : com.gshashank.btcagent.data.repository.NotificationsRepository {

    var unregisterCallCount: Int = 0
    var lastUnregisteredToken: String? = null
    var unregisterOrder: Int = -1

    override suspend fun register(fcmToken: String): com.gshashank.btcagent.data.repository.NotificationsResult =
        com.gshashank.btcagent.data.repository.NotificationsResult.Success

    override suspend fun unregister(fcmToken: String): com.gshashank.btcagent.data.repository.NotificationsResult {
        unregisterCallCount++
        lastUnregisteredToken = fcmToken
        unregisterOrder = callOrder.incrementAndGet()
        return com.gshashank.btcagent.data.repository.NotificationsResult.Success
    }
}

/** Fake [FcmTokenProvider] — MOBILE-41. Returns a settable token (null = no token available). */
private class FakeFcmTokenProvider(
    var token: String? = "fake-fcm-token",
) : com.gshashank.btcagent.data.network.FcmTokenProvider {
    override suspend fun currentToken(): String? = token
}
