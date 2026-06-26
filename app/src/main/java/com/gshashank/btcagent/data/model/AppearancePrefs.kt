package com.gshashank.btcagent.data.model

/**
 * Domain model for local appearance preferences — MOBILE-20.
 *
 * Stored in DataStore; no network I/O.
 */
data class AppearancePrefs(
    val darkMode: Boolean,
    val colorTheme: ColorTheme,
    val dashboardLayout: DashboardLayout,
    val biometricUnlock: Boolean,
)

enum class ColorTheme { BITCOIN, COBALT, VIOLET }

enum class DashboardLayout { HERO, GRID, TERMINAL }
