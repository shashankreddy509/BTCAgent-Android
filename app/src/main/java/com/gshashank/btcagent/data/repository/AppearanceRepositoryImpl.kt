package com.gshashank.btcagent.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gshashank.btcagent.data.model.ColorTheme
import com.gshashank.btcagent.data.model.DashboardLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Implements [AppearanceRepository] using DataStore<Preferences> — MOBILE-20.
 *
 * The [dataStore] is SEPARATE from catalog_prefs — injected via @Named("appearance_prefs").
 *
 * Defaults: darkMode=false, colorTheme=BITCOIN, dashboardLayout=HERO, biometricUnlock=false.
 * Enum prefs are stored as their .name strings; an unrecognized value falls back to the default
 * (catches IllegalArgumentException from enumValueOf).
 */
@Singleton
class AppearanceRepositoryImpl @Inject constructor(
    @Named("appearance_prefs") private val dataStore: DataStore<Preferences>,
) : AppearanceRepository {

    companion object {
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
        private val KEY_DASHBOARD_LAYOUT = stringPreferencesKey("dashboard_layout")
        private val KEY_BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock")
    }

    override val darkModeFlow: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_DARK_MODE] ?: false }

    override val colorThemeFlow: Flow<ColorTheme> =
        dataStore.data.map { prefs ->
            val name = prefs[KEY_COLOR_THEME]
            if (name != null) {
                try {
                    enumValueOf<ColorTheme>(name)
                } catch (e: IllegalArgumentException) {
                    ColorTheme.BITCOIN
                }
            } else {
                ColorTheme.BITCOIN
            }
        }

    override val dashboardLayoutFlow: Flow<DashboardLayout> =
        dataStore.data.map { prefs ->
            val name = prefs[KEY_DASHBOARD_LAYOUT]
            if (name != null) {
                try {
                    enumValueOf<DashboardLayout>(name)
                } catch (e: IllegalArgumentException) {
                    DashboardLayout.HERO
                }
            } else {
                DashboardLayout.HERO
            }
        }

    override val biometricUnlockFlow: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_BIOMETRIC_UNLOCK] ?: false }

    override suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    override suspend fun setColorTheme(theme: ColorTheme) {
        dataStore.edit { it[KEY_COLOR_THEME] = theme.name }
    }

    override suspend fun setDashboardLayout(layout: DashboardLayout) {
        dataStore.edit { it[KEY_DASHBOARD_LAYOUT] = layout.name }
    }

    override suspend fun setBiometricUnlock(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_UNLOCK] = enabled }
    }
}
