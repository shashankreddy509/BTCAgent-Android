package com.gshashank.btcagent.data.network

import kotlinx.serialization.Serializable

/**
 * DTO for GET /api/brief — MOBILE-9.
 *
 * The endpoint returns:
 *   { "timestamp": str|null, "text": str }
 */
@Serializable
data class BriefingDto(
    val timestamp: String? = null,
    val text: String = "",
)
