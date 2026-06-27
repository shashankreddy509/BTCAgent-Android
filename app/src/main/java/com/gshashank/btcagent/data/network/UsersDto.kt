package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for Users admin endpoints — MOBILE-21.
 *
 * All mapping to domain models lives in UsersRepositoryImpl.
 * These classes have zero domain or data.model imports.
 */

@Serializable
data class AdminUserDto(
    val uid: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    val mode: String,
    val broker: String,
    @SerialName("scanner_running") val scannerRunning: Boolean,
)

@Serializable
data class AllowlistDto(
    val emails: List<String>,
)

@Serializable
data class SetModeRequest(
    val mode: String,
)

@Serializable
data class AdminStatusResponseDto(
    val status: String,
)
