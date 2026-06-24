package com.gshashank.btcagent.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.data.model.PriceDirection
import com.gshashank.btcagent.data.repository.DashboardRepository
import com.gshashank.btcagent.data.repository.DashboardResult
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
 * ViewModel for the Dashboard screen — MOBILE-5.
 *
 * Combines live BTC price from [DashboardRepository.priceFlow] (WebSocket) with REST state
 * from [DashboardRepository.fetchState] and network status from [NetworkMonitor.isOnlineFlow].
 *
 * State machine:
 *  - [UiState.Loading] on init and after retry().
 *  - [UiState.Ready]   when a REST fetch succeeds (and/or a WS price tick arrives).
 *  - [UiState.Error]   when a REST fetch fails.
 *  - [UiState.Offline] when [NetworkMonitor.isOnlineFlow] emits false.
 *
 * WS price ticks update [DashboardData.btcPrice] and [DashboardData.priceDirection] in
 * [UiState.Ready] only — ticks received during Loading/Error/Offline are buffered in
 * [lastKnownPrice] and used when the next fetch completes.
 *
 * All three mutable fields below ([lastSuccessMs], [latestRestData], [lastKnownPrice]) are
 * confined to the Main thread: every coroutine that reads or writes them is launched on the
 * undispatched [viewModelScope] (Dispatchers.Main / the test main dispatcher), which serializes
 * them. Do NOT move any of these coroutines onto a background dispatcher without adding real
 * synchronization — there is no compiler guard against the resulting data race.
 *
 * A [delay(1L)] before the REST fetch creates a genuine virtual-time suspension point so that
 * Turbine can observe [UiState.Loading] before the fetch result arrives — same pattern as
 * [com.gshashank.btcagent.ui.gate.GateViewModel]. Tests drive it via Dispatchers.setMain(test).
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<DashboardData>> = _uiState.asStateFlow()

    /** Main-confined. Last successful REST fetch time in epoch-ms, for offline state. */
    private var lastSuccessMs: Long = 0L

    /** Main-confined. Latest REST data (excluding live WS price). */
    private var latestRestData: DashboardData? = null

    /**
     * Main-confined. Last known price — seeded from REST data, then updated by each WS tick.
     * Baseline for [PriceDirection] computation.
     */
    private var lastKnownPrice: Double? = null

    /** Tracks the in-flight REST fetch job so retry() can cancel stale requests. */
    private var fetchJob: Job? = null

    init {
        startPriceCollection()
        startNetworkMonitoring()
        doFetch()
    }

    private fun startPriceCollection() {
        viewModelScope.launch {
            repository.priceFlow().collect { price ->
                val newPrice = price.toDouble()
                val prev = lastKnownPrice
                val direction = when {
                    prev == null -> PriceDirection.Flat
                    newPrice > prev -> PriceDirection.Up
                    newPrice < prev -> PriceDirection.Down
                    else -> PriceDirection.Flat
                }
                lastKnownPrice = newPrice

                // Only update the UI if we already have REST data and the state is Ready.
                // Price ticks during Loading/Error/Offline are buffered in lastKnownPrice
                // and picked up when the next successful fetch completes.
                val data = latestRestData
                if (_uiState.value is UiState.Ready && data != null) {
                    _uiState.value = UiState.Ready(
                        data.copy(
                            btcPrice = newPrice,
                            priceDirection = direction,
                            // Always include a fresh timestamp so StateFlow emits even when
                            // btcPrice and priceDirection haven't changed (equal-price tick).
                            priceTickMs = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
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
                    // Back online — re-fetch if the last state was a connectivity-blocked one
                    // (Offline) OR a failed fetch (Error), so recovery is automatic either way.
                    val s = _uiState.value
                    if (s is UiState.Offline || s is UiState.Error) {
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
            // delay(1L) creates a real virtual-time suspension point so Turbine can observe
            // UiState.Loading before the fetch result arrives.
            // (Same technique as GateViewModel — yield() alone is insufficient with
            // UnconfinedTestDispatcher because that dispatcher resumes immediately after yield,
            // conflating Loading with the terminal state.)
            delay(1L)
            val result = repository.fetchState()
            when (result) {
                is DashboardResult.Success -> {
                    val restData = result.data
                    latestRestData = restData
                    // Seed lastKnownPrice from REST data on first successful fetch.
                    if (lastKnownPrice == null) {
                        lastKnownPrice = restData.btcPrice
                    }
                    lastSuccessMs = System.currentTimeMillis()
                    _uiState.value = UiState.Ready(
                        restData.copy(
                            btcPrice = lastKnownPrice ?: restData.btcPrice,
                            priceDirection = restData.priceDirection,
                        )
                    )
                }
                is DashboardResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = "Could not load dashboard",
                    )
                }
            }
        }
    }

    /** Resets to [UiState.Loading] and re-triggers the REST fetch. */
    fun retry() {
        doFetch()
    }
}
