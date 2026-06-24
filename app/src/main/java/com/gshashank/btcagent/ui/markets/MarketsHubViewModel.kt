package com.gshashank.btcagent.ui.markets

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface MarketsHubUiState {
    data object Stub : MarketsHubUiState
}

@HiltViewModel
class MarketsHubViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<MarketsHubUiState>(MarketsHubUiState.Stub)
    val uiState: StateFlow<MarketsHubUiState> = _uiState.asStateFlow()
}
