package com.gshashank.btcagent.ui.markets.liquidity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.LiquidityMapData
import com.gshashank.btcagent.data.repository.LiquidityRepository
import com.gshashank.btcagent.data.repository.LiquidityResult
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
 * ViewModel for the Liquidity Map screen — MOBILE-15.
 *
 * Starts in [UiState.Loading], fetches whenever the network comes online, and exposes
 * [uiState] as a [StateFlow].
 *
 * Monitors network connectivity via [NetworkMonitor.isOnlineFlow]:
 * - Online  → triggers [doFetch].
 * - Offline → transitions to [UiState.Offline].
 *
 * The Liquidity Map screen is only reachable when the catalog tile is enabled —
 * gating is at the tile/navigation layer, NOT inside this ViewModel.
 */
@HiltViewModel
class LiquidityMapViewModel @Inject constructor(
    private val repository: LiquidityRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<LiquidityMapData>>(UiState.Loading)
    val uiState: StateFlow<UiState<LiquidityMapData>> = _uiState.asStateFlow()

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
            // before the fetch result arrives (same technique as MarkovMatrixViewModel).
            delay(1L)
            _uiState.value = when (val result = repository.fetch()) {
                is LiquidityResult.Success -> {
                    lastSuccessMs = System.currentTimeMillis()
                    if (result.data.isEmpty) UiState.Empty
                    else UiState.Ready(result.data)
                }
                is LiquidityResult.Forbidden -> UiState.Error(
                    code = "ACCESS_DENIED",
                    message = "Access not approved",
                )
                is LiquidityResult.Error -> UiState.Error(
                    code = "ERR_FETCH",
                    message = result.message ?: "Could not load Liquidity Map data",
                )
            }
        }
    }

    /** Re-triggers the fetch from an error state. */
    fun retry() {
        doFetch()
    }
}
