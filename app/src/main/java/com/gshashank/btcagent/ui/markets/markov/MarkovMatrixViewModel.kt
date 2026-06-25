package com.gshashank.btcagent.ui.markets.markov

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.MarkovData
import com.gshashank.btcagent.data.repository.MarkovRepository
import com.gshashank.btcagent.data.repository.MarkovResult
import com.gshashank.btcagent.ui.components.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Markov Matrix screen — MOBILE-13.
 *
 * Starts in [UiState.Loading], fetches on init, and exposes [uiState] as a [StateFlow].
 * Monitors network connectivity via [NetworkMonitor.isOnlineFlow] and transitions to
 * [UiState.Offline] when connectivity is lost.
 *
 * On [UiState.Ready], sets [selectedTicker] to "BTC-USD" if present, or the first ticker
 * in the list otherwise. [selectedTicker] is null until the first successful fetch.
 *
 * The Markov Matrix screen is only reachable when the catalog tile is enabled —
 * gating is at the tile/navigation layer, NOT inside this ViewModel.
 */
@HiltViewModel
class MarkovMatrixViewModel @Inject constructor(
    private val repository: MarkovRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<MarkovData>>(UiState.Loading)
    val uiState: StateFlow<UiState<MarkovData>> = _uiState.asStateFlow()

    private val _selectedTicker = MutableStateFlow<String?>(null)
    val selectedTicker: StateFlow<String?> = _selectedTicker.asStateFlow()

    private var lastSuccessMs: Long = 0L
    private var fetchJob: Job? = null

    init {
        startNetworkMonitoring()
        doFetch()
    }

    private fun startNetworkMonitoring() {
        viewModelScope.launch {
            networkMonitor.isOnlineFlow.collect { online ->
                if (!online) {
                    _uiState.value = UiState.Offline(
                        lastUpdatedMs = lastSuccessMs,
                        hasCache = false,
                    )
                } else {
                    // Back online — refetch from any non-live state.
                    val s = _uiState.value
                    if (s is UiState.Offline || s is UiState.Error || s is UiState.Empty) {
                        doFetch()
                    }
                }
            }
        }
    }

    private fun doFetch() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            // delay(1L) creates a real virtual-time suspension point so Turbine observes Loading
            // before the fetch result arrives (same technique as OpenInterestViewModel).
            delay(1L)
            when (val result = repository.fetchTickers()) {
                is MarkovResult.Success -> {
                    // Stamp on ANY success (incl. empty) so offline banner shows a valid "ago" time.
                    lastSuccessMs = System.currentTimeMillis()
                    val data = result.data
                    if (data.isEmpty) {
                        _uiState.value = UiState.Empty
                    } else {
                        // Set selectedTicker: prefer "BTC-USD", else first ticker.
                        val defaultTicker = data.tickers
                            .firstOrNull { it.ticker == "BTC-USD" }?.ticker
                            ?: data.tickers.first().ticker
                        _selectedTicker.value = defaultTicker
                        _uiState.value = UiState.Ready(data)
                    }
                }
                is MarkovResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = result.message ?: "Could not load Markov Matrix data",
                    )
                }
            }
        }
    }

    /** Updates the selected ticker client-side — no refetch triggered. */
    fun onSelectTicker(ticker: String) {
        _selectedTicker.value = ticker
    }

    /** Re-triggers the fetch from an error state. */
    fun retry() {
        doFetch()
    }
}
