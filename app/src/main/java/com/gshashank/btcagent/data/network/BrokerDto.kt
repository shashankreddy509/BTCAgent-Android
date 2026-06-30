package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for broker settings endpoints — MOBILE-45.
 *
 * CRITICAL: [BrokerRowDto.connected] is Boolean? (nullable). JSON null must survive
 * deserialization as Kotlin null — it is NOT the same as false.
 * null = not probed (neutral "—" UI); false = probed and disconnected.
 */

@Serializable
data class BrokerSummaryDto(
    val brokers: List<BrokerRowDto> = emptyList(),
    val active: String? = null,
)

@Serializable
data class BrokerRowDto(
    val broker: String? = null,
    @SerialName("account_name") val accountName: String? = null,
    @SerialName("api_key_masked") val apiKeyMasked: String? = null,
    val configured: Boolean = false,
    val active: Boolean = false,
    val connected: Boolean? = null,  // MUST be Boolean? (nullable) — null ≠ false
)

@Serializable
data class BrokerSaveResponseDto(
    val status: String,
)
