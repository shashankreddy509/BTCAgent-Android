package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the Trading Control endpoints — MOBILE-18.
 */

@Serializable
data class StatusResponseDto(val status: String)

/**
 * Body for POST /api/trading/settings.
 *
 * Only the changed key is sent per call. Null fields are omitted from JSON because the shared
 * Json instance is configured with `explicitNulls = false` (same as [EditTpSlRequest] pattern).
 * Callers set exactly one field and leave the other null:
 *   - setMode → SettingsWriteRequest(mode = "live"|"paper")
 *   - setDepoAlerts → SettingsWriteRequest(depoEntryFilter = true|false)
 */
@Serializable
data class SettingsWriteRequest(
    val mode: String? = null,
    @SerialName("depo_entry_filter")
    val depoEntryFilter: Boolean? = null,
)

@Serializable
data class SetSettingsResponseDto(val status: String)
