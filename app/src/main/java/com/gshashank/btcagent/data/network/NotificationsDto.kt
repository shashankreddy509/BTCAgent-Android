package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the FCM push registration endpoints — MOBILE-41.
 *
 * [StatusResponseDto] ({"status": "registered"|"unregistered"}) is reused from
 * TradingControlDto.kt per the plan.
 */

@Serializable
data class RegisterRequest(
    @SerialName("fcm_token") val fcmToken: String,
    // No default: kotlinx.serialization omits fields that equal their declared default unless
    // encodeDefaults=true (not set on the shared Json instance), so "platform" must always be
    // supplied explicitly by the caller to guarantee it's serialized.
    val platform: String,
)

@Serializable
data class UnregisterRequest(
    @SerialName("fcm_token") val fcmToken: String,
)
