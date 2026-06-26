package com.gshashank.btcagent.ui.trade.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lightweight ViewModel that exposes the MANUAL_ENTRY catalog flag as a reactive [StateFlow].
 *
 * Used by [com.gshashank.btcagent.ui.trade.TradingControlScreen] to reactively show or hide the
 * "Manual Entry" navigation button without coupling [TradingControlViewModel] to CatalogRepository.
 */
@HiltViewModel
class ManualEntryFlagViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
) : ViewModel() {

    /**
     * Reactive MANUAL_ENTRY flag (id = 100007). Seeded with the sync value so the button renders
     * correctly before the first fetch lands. Updates when the catalog fetch lands.
     */
    val manualEntryEnabled: StateFlow<Boolean> = catalogRepository
        .isEnabledFlow(CatalogFlags.MANUAL_ENTRY, default = false)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = catalogRepository.isEnabled(CatalogFlags.MANUAL_ENTRY, default = false),
        )
}
