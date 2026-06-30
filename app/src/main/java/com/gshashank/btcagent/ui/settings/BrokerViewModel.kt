package com.gshashank.btcagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.repository.BrokerActionResult
import com.gshashank.btcagent.data.repository.BrokerRepository
import com.gshashank.btcagent.data.repository.BrokerResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Broker API section of Settings — MOBILE-45.
 *
 * Exposes:
 * - [brokerState] — Loading → Ready(brokerInfo) or Error after fetch.
 * - [actionResult] — nullable one-shot write feedback (null → Success/Error).
 *
 * Double-tap guard: [isReplacing] prevents concurrent in-flight PUT calls.
 * On successful PUT, a re-fetch is triggered so the UI shows the updated masked key.
 */
@HiltViewModel
class BrokerViewModel @Inject constructor(
    private val repository: BrokerRepository,
) : ViewModel() {

    private val _brokerState = MutableStateFlow<BrokerState>(BrokerState.Loading)
    val brokerState: StateFlow<BrokerState> = _brokerState.asStateFlow()

    private val _actionResult = MutableStateFlow<BrokerActionResult?>(null)
    val actionResult: StateFlow<BrokerActionResult?> = _actionResult.asStateFlow()

    @Volatile
    private var isReplacing = false

    init {
        viewModelScope.launch { loadBroker() }
    }

    private suspend fun loadBroker() {
        _brokerState.value = BrokerState.Loading
        // Suspend once so the Loading state is observable to collectors before the fetch
        // resolves (the unit tests assert this; harmless in production). Kept as delay(1L) — the
        // tests advance virtual time, and yield() doesn't give them the same observation point.
        delay(1L)
        when (val result = repository.fetchBroker()) {
            is BrokerResult.Success -> _brokerState.value = BrokerState.Ready(result.brokerInfo)
            is BrokerResult.Error -> _brokerState.value = BrokerState.Error(result.message)
        }
    }

    /** Clears the one-shot action result after the UI has consumed it. */
    fun clearActionResult() {
        _actionResult.value = null
    }

    fun replaceBroker(broker: String, apiKey: String, apiSecret: String) {
        if (isReplacing) return  // double-tap guard
        isReplacing = true
        viewModelScope.launch {
            try {
                val result = repository.replaceBroker(broker, apiKey, apiSecret)
                _actionResult.value = result
                if (result is BrokerActionResult.Success) {
                    loadBroker()  // re-fetch to get updated masked key
                }
            } finally {
                isReplacing = false
            }
        }
    }
}
