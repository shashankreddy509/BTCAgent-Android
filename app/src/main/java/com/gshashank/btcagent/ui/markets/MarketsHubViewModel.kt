package com.gshashank.btcagent.ui.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MarketsHubUiState {
    data object Stub : MarketsHubUiState
}

/**
 * ViewModel for the Markets hub — MOBILE-10 / MOBILE-13 / MOBILE-14 / MOBILE-15 / MOBILE-17.
 *
 * Exposes [isMarkovEnabled] as a reactive [StateFlow<Boolean>] backed by
 * [CatalogRepository.isEnabledFlow] for [CatalogFlags.MARKOV_MATRIX].
 *
 * Exposes [isVolumeProfileEnabled] as a reactive [StateFlow<Boolean>] backed by
 * [CatalogRepository.isEnabledFlow] for [CatalogFlags.VOLUME_PROFILE].
 *
 * Exposes [isLiquidityMapEnabled] as a reactive [StateFlow<Boolean>] backed by
 * [CatalogRepository.isEnabledFlow] for [CatalogFlags.LIQUIDITY_MAP].
 *
 * Exposes [isAnalyticsEnabled] as a reactive [StateFlow<Boolean>] backed by
 * [CatalogRepository.isEnabledFlow] for [CatalogFlags.ANALYTICS].
 *
 * Tiles are only visible when their respective flags are true
 * (default=false → absent flag hides the tile — instant rollback).
 *
 * All flows are seeded with the synchronous [CatalogRepository.isEnabled] result so
 * that the initial UI state is immediately correct (no flicker). The reactive flow then
 * replaces the initial value via a [viewModelScope.launch] coroutine that uses [delay(1L)]
 * to create a virtual-time suspension point — matching the technique in [MarkovMatrixViewModel]
 * — so that test subscribers (Turbine + [advanceUntilIdle]) can observe the initial value
 * before any flow emissions land.
 */
@HiltViewModel
class MarketsHubViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketsHubUiState>(MarketsHubUiState.Stub)
    val uiState: StateFlow<MarketsHubUiState> = _uiState.asStateFlow()

    private val _isMarkovEnabled = MutableStateFlow(
        catalogRepository.isEnabled(CatalogFlags.MARKOV_MATRIX),
    )

    /**
     * Emits true when [CatalogFlags.MARKOV_MATRIX] is ON; false when absent or OFF.
     * default=false because this flag is NOT security-sensitive — missing/failed fetch
     * falls back to OFF (tile hidden), not ON.
     */
    val isMarkovEnabled: StateFlow<Boolean> = _isMarkovEnabled.asStateFlow()

    private val _isVolumeProfileEnabled = MutableStateFlow(
        catalogRepository.isEnabled(CatalogFlags.VOLUME_PROFILE),
    )

    /**
     * Emits true when [CatalogFlags.VOLUME_PROFILE] is ON; false when absent or OFF.
     * default=false because this flag is NOT security-sensitive — missing/failed fetch
     * falls back to OFF (tile hidden), not ON.
     */
    val isVolumeProfileEnabled: StateFlow<Boolean> = _isVolumeProfileEnabled.asStateFlow()

    private val _isLiquidityMapEnabled = MutableStateFlow(
        catalogRepository.isEnabled(CatalogFlags.LIQUIDITY_MAP),
    )

    /**
     * Emits true when [CatalogFlags.LIQUIDITY_MAP] is ON; false when absent or OFF.
     * default=false because this flag is NOT security-sensitive — missing/failed fetch
     * falls back to OFF (tile hidden), not ON.
     */
    val isLiquidityMapEnabled: StateFlow<Boolean> = _isLiquidityMapEnabled.asStateFlow()

    private val _isAnalyticsEnabled = MutableStateFlow(
        catalogRepository.isEnabled(CatalogFlags.ANALYTICS),
    )

    /**
     * Emits true when [CatalogFlags.ANALYTICS] is ON; false when absent or OFF.
     * default=false because this flag is NOT security-sensitive — missing/failed fetch
     * falls back to OFF (tile hidden), not ON.
     */
    val isAnalyticsEnabled: StateFlow<Boolean> = _isAnalyticsEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            // delay(1L) creates a virtual-time suspension point so that Turbine subscribers in
            // tests observe the initial seeded value before the catalog flow starts emitting
            // updates. Without this, UnconfinedTestDispatcher would race ahead and update the
            // StateFlow before Turbine can buffer the initial state — breaking the toggle test.
            delay(1L)
            catalogRepository.isEnabledFlow(CatalogFlags.MARKOV_MATRIX, default = false)
                .collect { value -> _isMarkovEnabled.value = value }
        }
        viewModelScope.launch {
            delay(1L)
            catalogRepository.isEnabledFlow(CatalogFlags.VOLUME_PROFILE, default = false)
                .collect { value -> _isVolumeProfileEnabled.value = value }
        }
        viewModelScope.launch {
            delay(1L)
            catalogRepository.isEnabledFlow(CatalogFlags.LIQUIDITY_MAP, default = false)
                .collect { value -> _isLiquidityMapEnabled.value = value }
        }
        viewModelScope.launch {
            delay(1L)
            catalogRepository.isEnabledFlow(CatalogFlags.ANALYTICS, default = false)
                .collect { value -> _isAnalyticsEnabled.value = value }
        }
    }
}
