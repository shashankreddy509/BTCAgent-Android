package com.gshashank.btcagent.data.model

/**
 * Domain model for user settings — MOBILE-20 / MOBILE-42.
 *
 * Zero data.network imports — all mapping lives in SettingsRepositoryImpl.
 * Scanner fields ([scanIntervalMin], [tfMin], [tfMax], [patterns]) are display-only (MOBILE-42),
 * read from GET /api/settings/user (the same prefs doc the Save writes back to).
 */
data class UserSettings(
    val qty: Int?,
    val maxSl: Double?,
    val minTp: Double?,
    val maxConcurrent: Int?,
    val mode: ExecutionMode?,
    // Scanner parameters — display only (MOBILE-42)
    val scanIntervalMin: Int? = null,
    val tfMin: Int? = null,
    val tfMax: Int? = null,
    val patterns: List<String>? = null,
)
