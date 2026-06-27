package com.gshashank.btcagent.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gshashank.btcagent.data.model.ColorTheme
import com.gshashank.btcagent.data.model.DashboardLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * JVM unit tests for [AppearanceRepositoryImpl] — MOBILE-20 / MOBILE-23.
 *
 * Uses a real [DataStore]<[Preferences]> backed by a unique temporary directory per test
 * (same pattern as [CatalogRepositoryImplTest]). This avoids the need for Android mocks and
 * exercises the actual DataStore read/write round-trips.
 *
 * Appearance prefs are DataStore-local (no network). The four prefs are:
 *   - darkMode      (Boolean, default = false)
 *   - colorTheme    (ColorTheme enum, default = ColorTheme.BITCOIN)
 *   - dashboardLayout (DashboardLayout enum, default = DashboardLayout.HERO)
 *   - biometricUnlock (Boolean, default = false)
 *
 * MOBILE-23 adds:
 *   - hasSeenOnboarding (Boolean, default = false, key "has_seen_onboarding")
 *
 * All tests MUST fail (red) until [AppearanceRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1.  Default darkMode = false when DataStore is empty (unset)
 *   2.  setDarkMode(true) → darkModeFlow emits true
 *   3.  setDarkMode(false) → darkModeFlow emits false (round-trip toggle)
 *   4.  setColorTheme(COBALT) → colorThemeFlow emits COBALT
 *   5.  setDashboardLayout(GRID) → dashboardLayoutFlow emits GRID
 *   6.  setBiometricUnlock(true) → biometricUnlockFlow emits true
 *   7.  Default colorTheme = BITCOIN when DataStore is empty
 *   8.  Default dashboardLayout = HERO when DataStore is empty
 *   9.  Default biometricUnlock = false when DataStore is empty
 *   10. hasSeenOnboardingFlow defaults to false when DataStore is empty (MOBILE-23)
 *   11. setHasSeenOnboarding(true) → hasSeenOnboardingFlow emits true (MOBILE-23)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceRepositoryImplTest {

    // StandardTestDispatcher: coroutines scheduled but NOT run eagerly.
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: AppearanceRepositoryImpl

    // Unique temp dir per test so DataStore files never bleed between tests.
    private val tempDir: File = File(
        System.getProperty("java.io.tmpdir"),
        "appearance_test_${UUID.randomUUID()}",
    ).also { it.mkdirs() }

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempDir, "appearance_prefs.preferences_pb") },
        )

        repository = AppearanceRepositoryImpl(
            dataStore = dataStore,
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 1. Default darkMode = false when DataStore is empty (unset)
    // =========================================================================

    @Test
    fun `darkModeFlow defaults to false when DataStore is empty`() = testScope.runTest {
        advanceUntilIdle()

        val darkMode = repository.darkModeFlow.first()

        assertFalse(
            "darkModeFlow must default to false when the DataStore has no persisted value",
            darkMode,
        )
    }

    // =========================================================================
    // 2. setDarkMode(true) → darkModeFlow emits true
    // =========================================================================

    @Test
    fun `setDarkMode true causes darkModeFlow to emit true`() = testScope.runTest {
        repository.setDarkMode(true)
        advanceUntilIdle()

        val darkMode = repository.darkModeFlow.first()

        assertTrue(
            "darkModeFlow must emit true after setDarkMode(true) is called",
            darkMode,
        )
    }

    // =========================================================================
    // 3. setDarkMode(false) → darkModeFlow emits false (round-trip toggle)
    // =========================================================================

    @Test
    fun `setDarkMode false after true round-trips back to false`() = testScope.runTest {
        // Set to true first
        repository.setDarkMode(true)
        advanceUntilIdle()

        val afterTrue = repository.darkModeFlow.first()
        assertTrue(
            "Precondition: darkModeFlow must be true after setDarkMode(true), got $afterTrue",
            afterTrue,
        )

        // Then toggle back to false
        repository.setDarkMode(false)
        advanceUntilIdle()

        val afterFalse = repository.darkModeFlow.first()
        assertFalse(
            "darkModeFlow must emit false after setDarkMode(false) — round-trip toggle must work",
            afterFalse,
        )
    }

    // =========================================================================
    // 4. setColorTheme(COBALT) → colorThemeFlow emits COBALT
    // =========================================================================

    @Test
    fun `setColorTheme COBALT causes colorThemeFlow to emit COBALT`() = testScope.runTest {
        repository.setColorTheme(ColorTheme.COBALT)
        advanceUntilIdle()

        val colorTheme = repository.colorThemeFlow.first()

        assertEquals(
            "colorThemeFlow must emit ColorTheme.COBALT after setColorTheme(COBALT) is called",
            ColorTheme.COBALT,
            colorTheme,
        )
    }

    @Test
    fun `setColorTheme VIOLET causes colorThemeFlow to emit VIOLET`() = testScope.runTest {
        repository.setColorTheme(ColorTheme.VIOLET)
        advanceUntilIdle()

        val colorTheme = repository.colorThemeFlow.first()

        assertEquals(
            "colorThemeFlow must emit ColorTheme.VIOLET after setColorTheme(VIOLET) is called",
            ColorTheme.VIOLET,
            colorTheme,
        )
    }

    @Test
    fun `setColorTheme BITCOIN causes colorThemeFlow to emit BITCOIN`() = testScope.runTest {
        // First set to COBALT, then switch back to BITCOIN
        repository.setColorTheme(ColorTheme.COBALT)
        advanceUntilIdle()

        repository.setColorTheme(ColorTheme.BITCOIN)
        advanceUntilIdle()

        val colorTheme = repository.colorThemeFlow.first()

        assertEquals(
            "colorThemeFlow must emit ColorTheme.BITCOIN after switching back from COBALT",
            ColorTheme.BITCOIN,
            colorTheme,
        )
    }

    // =========================================================================
    // 5. setDashboardLayout(GRID) → dashboardLayoutFlow emits GRID
    // =========================================================================

    @Test
    fun `setDashboardLayout GRID causes dashboardLayoutFlow to emit GRID`() = testScope.runTest {
        repository.setDashboardLayout(DashboardLayout.GRID)
        advanceUntilIdle()

        val layout = repository.dashboardLayoutFlow.first()

        assertEquals(
            "dashboardLayoutFlow must emit DashboardLayout.GRID after setDashboardLayout(GRID)",
            DashboardLayout.GRID,
            layout,
        )
    }

    @Test
    fun `setDashboardLayout TERMINAL causes dashboardLayoutFlow to emit TERMINAL`() =
        testScope.runTest {
            repository.setDashboardLayout(DashboardLayout.TERMINAL)
            advanceUntilIdle()

            val layout = repository.dashboardLayoutFlow.first()

            assertEquals(
                "dashboardLayoutFlow must emit DashboardLayout.TERMINAL after setDashboardLayout(TERMINAL)",
                DashboardLayout.TERMINAL,
                layout,
            )
        }

    // =========================================================================
    // 6. setBiometricUnlock(true) → biometricUnlockFlow emits true
    // =========================================================================

    @Test
    fun `setBiometricUnlock true causes biometricUnlockFlow to emit true`() = testScope.runTest {
        repository.setBiometricUnlock(true)
        advanceUntilIdle()

        val biometric = repository.biometricUnlockFlow.first()

        assertTrue(
            "biometricUnlockFlow must emit true after setBiometricUnlock(true) is called",
            biometric,
        )
    }

    @Test
    fun `setBiometricUnlock false after true round-trips back to false`() = testScope.runTest {
        repository.setBiometricUnlock(true)
        advanceUntilIdle()

        val afterTrue = repository.biometricUnlockFlow.first()
        assertTrue(
            "Precondition: biometricUnlockFlow must be true after setBiometricUnlock(true)",
            afterTrue,
        )

        repository.setBiometricUnlock(false)
        advanceUntilIdle()

        val afterFalse = repository.biometricUnlockFlow.first()
        assertFalse(
            "biometricUnlockFlow must emit false after setBiometricUnlock(false)",
            afterFalse,
        )
    }

    // =========================================================================
    // 7. Default colorTheme = BITCOIN when DataStore is empty
    // =========================================================================

    @Test
    fun `colorThemeFlow defaults to BITCOIN when DataStore is empty`() = testScope.runTest {
        advanceUntilIdle()

        val colorTheme = repository.colorThemeFlow.first()

        assertEquals(
            "colorThemeFlow must default to ColorTheme.BITCOIN when the DataStore has no persisted value",
            ColorTheme.BITCOIN,
            colorTheme,
        )
    }

    // =========================================================================
    // 8. Default dashboardLayout = HERO when DataStore is empty
    // =========================================================================

    @Test
    fun `dashboardLayoutFlow defaults to HERO when DataStore is empty`() = testScope.runTest {
        advanceUntilIdle()

        val layout = repository.dashboardLayoutFlow.first()

        assertEquals(
            "dashboardLayoutFlow must default to DashboardLayout.HERO when the DataStore has no persisted value",
            DashboardLayout.HERO,
            layout,
        )
    }

    // =========================================================================
    // 9. Default biometricUnlock = false when DataStore is empty
    // =========================================================================

    @Test
    fun `biometricUnlockFlow defaults to false when DataStore is empty`() = testScope.runTest {
        advanceUntilIdle()

        val biometric = repository.biometricUnlockFlow.first()

        assertFalse(
            "biometricUnlockFlow must default to false when the DataStore has no persisted value",
            biometric,
        )
    }

    // =========================================================================
    // 10. hasSeenOnboardingFlow defaults to false when DataStore is empty — MOBILE-23
    // =========================================================================

    @Test
    fun `hasSeenOnboardingFlow defaults false when DataStore is empty`() = testScope.runTest {
        advanceUntilIdle()

        val hasSeen = repository.hasSeenOnboardingFlow.first()

        assertFalse(
            "hasSeenOnboardingFlow must default to false on a fresh DataStore (first launch shows onboarding)",
            hasSeen,
        )
    }

    // =========================================================================
    // 11. setHasSeenOnboarding(true) → hasSeenOnboardingFlow emits true — MOBILE-23
    // =========================================================================

    @Test
    fun `setHasSeenOnboarding true emits true`() = testScope.runTest {
        repository.setHasSeenOnboarding(true)
        advanceUntilIdle()

        val hasSeen = repository.hasSeenOnboardingFlow.first()

        assertTrue(
            "hasSeenOnboardingFlow must emit true after setHasSeenOnboarding(true) — onboarding must not re-show",
            hasSeen,
        )
    }
}
