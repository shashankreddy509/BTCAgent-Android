package com.gshashank.btcagent.data.network

import kotlinx.serialization.Serializable

/**
 * Body of `GET /api/access/check`. The endpoint always returns HTTP 200 for an
 * authenticated user; allow-list membership is carried in [allowed], not the status code.
 *
 * - non-approved → `{"allowed": false, "admin": false}`
 * - approved     → `{"allowed": true,  "admin": false}`
 * - owner/admin  → `{"allowed": true,  "admin": true}`
 */
@Serializable
data class AccessCheckDto(
    val allowed: Boolean = false,
    val admin: Boolean = false,
)
