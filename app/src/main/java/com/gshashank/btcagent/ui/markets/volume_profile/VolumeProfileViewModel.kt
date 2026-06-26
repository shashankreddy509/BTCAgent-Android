package com.gshashank.btcagent.ui.markets.volume_profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.Timeframe
import com.gshashank.btcagent.data.model.VolumeProfileData
import com.gshashank.btcagent.data.repository.VolumeProfileRepository
import com.gshashank.btcagent.data.repository.VolumeProfileResult
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
 * ViewModel for the Volume Profile screen — MOBILE-14.
 *
 * Starts in [UiState.Loading], fetches whenever the network comes online, and exposes
 * [uiState] as a [StateFlow].
 *
 * Monitors network connectivity via [NetworkMonitor.isOnlineFlow]:
 * - Online  → triggers [doFetch].
 * - Offline → transitions to [UiState.Offline].
 *
 * Also exposes [selectedTimeframe] for the timeframe chip UI and [onSelectTimeframe] to
 * update it.
 *
 * The Volume Profile screen is only reachable when the catalog tile is enabled —
 * gating is at the tile/navigation layer (MarketsHubViewModel), NOT inside this ViewModel.
 */
@HiltViewModel
class VolumeProfileViewModel @Inject constructor(
    private val repository: VolumeProfileRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<VolumeProfileData>>(UiState.Loading)
    val uiState: StateFlow<UiState<VolumeProfileData>> = _uiState.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.H4)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    private var lastSuccessMs: Long = 0L
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            networkMonitor.isOnlineFlow.collect { isOnline ->
                if (!isOnline) {
                    _uiState.value = UiState.Offline(
                        lastUpdatedMs = lastSuccessMs,
                        hasCache = false,
                    )
                } else {
                    doFetch()
                }
            }
        }
    }

    private fun doFetch() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            // Emit Loading synchronously so Turbine/collectors observe it before the fetch result.
            _uiState.value = UiState.Loading
            // delay(1L) creates a real virtual-time suspension point so Turbine observes Loading
            // before the fetch result arrives (same technique as LiquidityMapViewModel).
            delay(1L)
            _uiState.value = when (val result = repository.fetch()) {
                is VolumeProfileResult.Success -> {
                    lastSuccessMs = System.currentTimeMillis()
                    if (result.data.isEmpty) UiState.Empty
                    else UiState.Ready(result.data)
                }
                is VolumeProfileResult.Error -> UiState.Error(
                    code = "ERR_FETCH",
                    message = result.message ?: "Could not load Volume Profile data",
                )
            }
        }
    }

    /** Updates the currently selected timeframe. */
    fun onSelectTimeframe(tf: Timeframe) {
        _selectedTimeframe.value = tf
    }

    /** Re-triggers the fetch from an error state. */
    fun retry() {
        doFetch()
    }
}
