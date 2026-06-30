package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.BrokerInfo

/**
 * Sealed result types for BrokerRepository operations — MOBILE-45.
 */
sealed class BrokerResult {
    data class Success(val brokerInfo: BrokerInfo?) : BrokerResult()
    data class Error(val message: String) : BrokerResult()
}

sealed class BrokerActionResult {
    object Success : BrokerActionResult()
    data class Error(val message: String) : BrokerActionResult()
}
