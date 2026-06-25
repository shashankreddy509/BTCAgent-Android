package com.gshashank.btcagent.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.ReportsData
import com.gshashank.btcagent.data.repository.ReportsRepository
import com.gshashank.btcagent.data.repository.ReportsResult
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
 * ViewModel for the Reports screen — MOBILE-7.
 *
 * Starts in [UiState.Loading], fetches on init, and exposes [uiState] as a [StateFlow].
 * Monitors network connectivity via [NetworkMonitor.isOnlineFlow] and transitions to
 * [UiState.Offline] when connectivity is lost.
 *
 * Mirrors [com.gshashank.btcagent.ui.positions.PositionsListViewModel] pattern exactly.
 *
 * A [delay(1L)] before the fetch creates a real virtual-time suspension point so Turbine
 * can observe [UiState.Loading] before the fetch result arrives.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ReportsRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ReportsData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReportsData>> = _uiState.asStateFlow()

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
                    // Back online — refetch from any non-live state (Offline/Error, and Empty:
                    // new trades may have closed while disconnected).
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
            // before the fetch result arrives (same technique as DashboardViewModel / PositionsListViewModel).
            delay(1L)
            when (val result = repository.fetchReports()) {
                is ReportsResult.Success -> {
                    // Stamp on ANY success (incl. empty) — else a later offline banner computes
                    // "ago" from epoch 0 (1970) when the first successful fetch had no trades.
                    lastSuccessMs = System.currentTimeMillis()
                    val data = result.data
                    _uiState.value =
                        if (data.trades.isEmpty()) UiState.Empty else UiState.Ready(data)
                }
                is ReportsResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = result.message ?: "Could not load reports",
                    )
                }
            }
        }
    }

    /** Re-triggers the fetch from an error state. */
    fun retry() {
        doFetch()
    }
}
