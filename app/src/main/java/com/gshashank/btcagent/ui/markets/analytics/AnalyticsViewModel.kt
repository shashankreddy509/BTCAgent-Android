package com.gshashank.btcagent.ui.markets.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.model.AnalyticsData
import com.gshashank.btcagent.data.repository.AnalyticsRepository
import com.gshashank.btcagent.data.repository.AnalyticsResult
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
 * ViewModel for the Analytics screen — MOBILE-17.
 *
 * Starts in [UiState.Loading], fetches on init, and exposes [uiState] as a [StateFlow].
 * [delay(1L)] in [doFetch] creates a virtual-time suspension point so Turbine subscribers
 * observe the Loading state before the fetch result arrives.
 *
 * Gating is at the tile/nav layer (MarketsHubScreen) — this ViewModel does not read catalog flags.
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: AnalyticsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AnalyticsData>>(UiState.Loading)
    val uiState: StateFlow<UiState<AnalyticsData>> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            delay(1L)
            doFetch()
        }
    }

    private suspend fun doFetch() {
        _uiState.value = when (val result = repository.fetch()) {
            is AnalyticsResult.Success -> {
                val data = result.data
                if (data.isEmpty) UiState.Empty else UiState.Ready(data)
            }
            is AnalyticsResult.Error -> UiState.Error(
                code = "ERR_FETCH",
                message = result.message ?: "Could not load analytics data",
            )
        }
    }

    /** Re-triggers the fetch. Resets to Loading before re-fetching. */
    fun retry() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(1L)
            doFetch()
        }
    }
}
