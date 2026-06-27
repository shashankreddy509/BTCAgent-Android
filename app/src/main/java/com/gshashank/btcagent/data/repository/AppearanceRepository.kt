package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ColorTheme
import com.gshashank.btcagent.data.model.DashboardLayout
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for local appearance preferences — MOBILE-20.
 *
 * All preferences are DataStore-backed (no network I/O).
 */
interface AppearanceRepository {
    val darkModeFlow: Flow<Boolean>
    val colorThemeFlow: Flow<ColorTheme>
    val dashboardLayoutFlow: Flow<DashboardLayout>
    val biometricUnlockFlow: Flow<Boolean>
    val hasSeenOnboardingFlow: Flow<Boolean>

    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setColorTheme(theme: ColorTheme)
    suspend fun setDashboardLayout(layout: DashboardLayout)
    suspend fun setBiometricUnlock(enabled: Boolean)
    suspend fun setHasSeenOnboarding(seen: Boolean)
}
