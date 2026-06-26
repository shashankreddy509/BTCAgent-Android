package com.gshashank.btcagent.data.model

/**
 * Domain model for user settings — MOBILE-20.
 *
 * Zero data.network imports — all mapping lives in SettingsRepositoryImpl.
 * [brokerKeys] contains masked display strings (e.g. "ABCD****WXYZ") — read-only, never sent back.
 */
data class UserSettings(
    val qty: Int?,
    val maxSl: Double?,
    val minTp: Double?,
    val maxConcurrent: Int?,
    val mode: ExecutionMode?,
    val brokerKeys: List<String>,
)
