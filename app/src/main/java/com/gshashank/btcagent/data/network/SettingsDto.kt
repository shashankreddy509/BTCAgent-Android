package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for user settings endpoints — MOBILE-20.
 *
 * Null fields are omitted from PUT bodies because the shared Json is configured with
 * explicitNulls = false — sparse bodies only send the changed fields.
 *
 * MASKED-KEY GUARD: broker_keys containing "****" are read-only display strings returned by
 * the server; they are NEVER included in the write request body (UserSettingsWriteRequest
 * has no broker_keys field). The masked sentinel must never reach the server.
 */

@Serializable
data class UserSettingsDto(
    val qty: Int? = null,
    @SerialName("max_sl") val maxSl: Double? = null,
    @SerialName("min_tp") val minTp: Double? = null,
    @SerialName("max_concurrent") val maxConcurrent: Int? = null,
    val mode: String? = null,
    @SerialName("broker_keys") val brokerKeys: List<String> = emptyList(),
)

/**
 * Write request body for PUT api/settings/user.
 *
 * broker_keys is intentionally absent — masked display strings received from the server
 * must never be forwarded back. mode is a plain string ("live"/"paper") converted from
 * the domain enum in SettingsRepositoryImpl; enum conversion is inherently safe from "****".
 */
@Serializable
data class UserSettingsWriteRequest(
    val qty: Int? = null,
    @SerialName("max_sl") val maxSl: Double? = null,
    @SerialName("min_tp") val minTp: Double? = null,
    @SerialName("max_concurrent") val maxConcurrent: Int? = null,
    val mode: String? = null,
)

@Serializable
data class SaveSettingsResponseDto(val status: String)
