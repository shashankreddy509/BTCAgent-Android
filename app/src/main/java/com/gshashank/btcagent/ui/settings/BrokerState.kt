package com.gshashank.btcagent.ui.settings

import com.gshashank.btcagent.data.model.BrokerInfo

/**
 * UI state for the Broker API section — MOBILE-45.
 */
sealed class BrokerState {
    object Loading : BrokerState()
    data class Ready(val brokerInfo: BrokerInfo?) : BrokerState()
    data class Error(val message: String) : BrokerState()
}
