package com.gshashank.btcagent.ui.positions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.repository.PositionsRepository
import com.gshashank.btcagent.data.repository.PositionsResult
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
 * Screen-level data for the Positions list screen.
 *
 * @param unrealizedTotal  Sum of [Position.pnl] across all open positions.
 * @param exposureTotal    Sum of (entryPrice * qty * contractSize) per position.
 * @param positions        The full list of open positions.
 * @param openCount        Number of open positions (equals positions.size) — MOBILE-43.
 *                         Default 0 preserves backwards-compatibility for callers that omit it.
 * @param todayPnl         Sum of closed P&L for trades closed today — MOBILE-43.
 *                         Default 0.0 preserves backwards-compatibility.
 * @param mode             Trading mode ("paper" or "live") from settings — MOBILE-43.
 *                         Default "paper" preserves backwards-compatibility.
 */
data class PositionsScreenData(
    val unrealizedTotal: Double,
    val exposureTotal: Double,
    val positions: List<Position>,
    val openCount: Int = 0,
    val todayPnl: Double = 0.0,
    val mode: String = "paper",
)

/**
 * ViewModel for the Positions list screen — MOBILE-6, MOBILE-43.
 *
 * Starts in [UiState.Loading], fetches on init, and exposes [uiState] as a [StateFlow].
 * Monitors network connectivity via [NetworkMonitor.isOnlineFlow] and transitions to
 * [UiState.Offline] when connectivity is lost.
 *
 * MOBILE-43: todayPnl and mode are forwarded from PositionsResult.Success into PositionsScreenData
 * so the header row can display them without a second repository call.
 */
@HiltViewModel
class PositionsListViewModel @Inject constructor(
    private val repository: PositionsRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<PositionsScreenData>>(UiState.Loading)
    val uiState: StateFlow<UiState<PositionsScreenData>> = _uiState.asStateFlow()

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
            // delay(1L) creates a real virtual-time suspension point so Turbine observes Loading
            // before the fetch result arrives (same technique as DashboardViewModel).
            delay(1L)
            when (val result = repository.fetchPositions()) {
                is PositionsResult.Success -> {
                    // Always Ready on a successful fetch — even with zero positions — so the
                    // header + summary cards (the mock chrome) stay visible. The Ready branch
                    // shows a "No open positions" message in the list area when empty.
                    lastSuccessMs = System.currentTimeMillis()
                    _uiState.value = UiState.Ready(
                        result.positions.toScreenData(
                            todayPnl = result.todayPnl,
                            mode = result.mode,
                        )
                    )
                }
                is PositionsResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = result.message ?: "Could not load positions",
                    )
                }
            }
        }
    }

    /** Re-triggers the fetch from an error state. */
    fun retry() {
        doFetch()
    }

    private fun List<Position>.toScreenData(todayPnl: Double, mode: String): PositionsScreenData {
        val unrealizedTotal = sumOf { it.pnl }
        val exposureTotal = sumOf { position ->
            val effectiveSize = if (position.contractSize > 0.0) {
                position.qty * position.contractSize
            } else {
                position.qty
            }
            position.entryPrice * effectiveSize
        }
        return PositionsScreenData(
            unrealizedTotal = unrealizedTotal,
            exposureTotal = exposureTotal,
            positions = this,
            openCount = this.size,
            todayPnl = todayPnl,
            mode = mode,
        )
    }
}
