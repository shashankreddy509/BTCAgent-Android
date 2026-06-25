package com.gshashank.btcagent.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.ScanDirection
import com.gshashank.btcagent.data.model.ScanSignal
import com.gshashank.btcagent.data.model.ScannerData
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessResult
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.ScannerRepository
import com.gshashank.btcagent.data.repository.ScannerResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
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
 * ViewModel for the Scanner screen — MOBILE-8.
 *
 * Starts in [UiState.Loading], fetches on init, and exposes [uiState] as a [StateFlow].
 * Monitors network connectivity via [NetworkMonitor.isOnlineFlow] and transitions to
 * [UiState.Offline] when connectivity is lost.
 *
 * Filtering is applied in-VM: [setFilter] re-emits a filtered [ScannerData] copy so
 * [uiState] always reflects the currently active filter.
 *
 * [canTrigger] is derived from [AccessRepository.checkAccess] — true only for admins.
 * [triggerScan] is locally gated by [canTrigger] as defense-in-depth before calling the repo.
 *
 * A [delay(1L)] before the fetch creates a real virtual-time suspension point so Turbine
 * can observe [UiState.Loading] before the fetch result arrives.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: ScannerRepository,
    private val accessRepository: AccessRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ScannerData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ScannerData>> = _uiState.asStateFlow()

    private val _canTrigger = MutableStateFlow(false)
    val canTrigger: StateFlow<Boolean> = _canTrigger.asStateFlow()

    private val _triggerState = MutableStateFlow<ActionResultUiState?>(null)
    val triggerState: StateFlow<ActionResultUiState?> = _triggerState.asStateFlow()

    private val _activeFilter = MutableStateFlow(ScanFilter.All)
    val activeFilter: StateFlow<ScanFilter> = _activeFilter.asStateFlow()

    /** Raw (unfiltered) data from the last successful fetch — used when reapplying the filter. */
    private var rawData: ScannerData? = null

    private var lastSuccessMs: Long = 0L
    private var fetchJob: Job? = null
    private var triggerJob: Job? = null

    init {
        viewModelScope.launch { checkAdminAccess() }
        startNetworkMonitoring()
        doFetch()
    }

    private suspend fun checkAdminAccess() {
        val result = accessRepository.checkAccess()
        _canTrigger.value = result is AccessResult.Allowed && result.admin
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
            // before the fetch result arrives (same technique as ReportsViewModel / PositionsListViewModel).
            delay(1L)
            when (val result = repository.fetchScan()) {
                is ScannerResult.Success -> {
                    lastSuccessMs = System.currentTimeMillis()
                    rawData = result.data
                    applyFilter(result.data, _activeFilter.value)
                }
                is ScannerResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = result.message ?: "Could not load scanner data",
                    )
                }
            }
        }
    }

    /**
     * Applies the given [filter] to [data] and updates [uiState].
     * An empty filtered result maps to [UiState.Empty]; otherwise [UiState.Ready].
     */
    private fun applyFilter(data: ScannerData, filter: ScanFilter) {
        val filtered = when (filter) {
            ScanFilter.All -> data.signals
            ScanFilter.Bullish -> data.signals.filter { it.direction == ScanDirection.Bullish }
            ScanFilter.Bearish -> data.signals.filter { it.direction == ScanDirection.Bearish }
            ScanFilter.Depo -> data.signals.filter { it.depoLine != null }
        }
        val filteredData = data.copy(signals = filtered)
        _uiState.value = if (filtered.isEmpty()) UiState.Empty else UiState.Ready(filteredData)
    }

    /** Re-triggers the fetch from an error or offline state. */
    fun retry() {
        doFetch()
    }

    /**
     * Updates the active filter and re-applies it to the current raw data.
     * No-op if there is no successfully fetched data yet.
     */
    fun setFilter(filter: ScanFilter) {
        _activeFilter.value = filter
        val data = rawData ?: return
        applyFilter(data, filter)
    }

    /**
     * Triggers a scanner run.
     *
     * Local defense-in-depth: if [canTrigger] is false the call is refused immediately with an
     * [ActionResultUiState.Error] — the server would also return 403, but this surfaces a clear
     * message without an unnecessary network round-trip.
     *
     * On success, re-fetches the scan results so the list updates automatically.
     *
     * A double-tap is ignored while a trigger is already in flight, so a single tap can't fire
     * two concurrent POST + refetch chains (same guard pattern as PositionDetailViewModel).
     */
    fun triggerScan() {
        if (!_canTrigger.value) {
            _triggerState.value = ActionResultUiState.Error(
                code = 403,
                message = "Not authorized to trigger scan",
            )
            return
        }
        if (triggerJob?.isActive == true) return
        triggerJob = viewModelScope.launch {
            when (val result = repository.triggerScan()) {
                is ActionResult.Success -> {
                    _triggerState.value = ActionResultUiState.Success
                    doFetch()
                }
                is ActionResult.Error -> {
                    _triggerState.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }
}
