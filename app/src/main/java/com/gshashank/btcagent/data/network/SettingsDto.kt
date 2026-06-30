package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for user settings endpoints — MOBILE-20 / MOBILE-42.
 *
 * Null fields are omitted from PUT bodies because the shared Json is configured with
 * explicitNulls = false — sparse bodies only send the changed fields.
 *
 * MOBILE-42: broker_keys removed — the server never emits it and it was dead code.
 *   Scanner fields (scan_interval_min, tf_min, tf_max, patterns) added for display.
 */

@Serializable
data class UserSettingsDto(
    val qty: Int? = null,
    @SerialName("max_sl") val maxSl: Double? = null,
    @SerialName("min_tp") val minTp: Double? = null,
    @SerialName("max_concurrent") val maxConcurrent: Int? = null,
    val mode: String? = null,
    // Scanner parameter fields — display only (MOBILE-42)
    @SerialName("scan_interval_min") val scanIntervalMin: Int? = null,
    // tf_min / tf_max are bare integers (minutes) in the backend JSON, not strings (verified).
    @SerialName("tf_min") val tfMin: Int? = null,
    @SerialName("tf_max") val tfMax: Int? = null,
    val patterns: List<String>? = null,
)

/**
 * Write request body for PUT api/settings/user.
 *
 * broker_keys is intentionally absent — masked display strings received from the server
 * must never be forwarded back. mode is a plain string ("live"/"paper") converted from
 * the domain enum in SettingsRepositoryImpl; enum conversion is inherently safe from "****".
 * Scanner fields are display-only — not included in PUT body (MOBILE-42).
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
