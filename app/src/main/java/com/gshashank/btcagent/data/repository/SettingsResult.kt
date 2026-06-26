package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.UserSettings

/**
 * Result type returned by [SettingsRepository.fetchUserSettings] — MOBILE-20.
 */
sealed class SettingsResult {
    data class Success(val settings: UserSettings) : SettingsResult()
    data class Error(val message: String) : SettingsResult()
}
