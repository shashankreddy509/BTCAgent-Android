package com.gshashank.btcagent.data.model

/**
 * Domain model representing a single user in the admin Users screen — MOBILE-21.
 *
 * Zero data.network imports — all mapping lives in UsersRepositoryImpl.
 * Reuses [ExecutionMode] from TradingControlData.
 */
data class AdminUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val mode: ExecutionMode,
    val scannerRunning: Boolean,
)
