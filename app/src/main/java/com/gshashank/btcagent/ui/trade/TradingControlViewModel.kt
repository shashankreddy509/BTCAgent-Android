package com.gshashank.btcagent.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.TradingControlData
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.TradingControlRepository
import com.gshashank.btcagent.data.repository.TradingControlResult
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
 * ViewModel for the Trading Control screen — MOBILE-18.
 *
 * Exposes:
 * - [uiState] — [UiState.Loading] → [UiState.Ready] / [UiState.Error] after fetch.
 * - [actionResult] — nullable [ActionResultUiState] for one-shot write feedback (snackbar).
 * - [pendingLiveMode] — true while the LIVE confirm dialog is open.
 *
 * Double-tap guards: [_startJob] / [_stopJob] prevent concurrent in-flight calls.
 * LIVE mode switch requires explicit [confirmLiveMode]; [cancelLiveMode] aborts with no write.
 * All write successes trigger a state refresh.
 */
@HiltViewModel
class TradingControlViewModel @Inject constructor(
    private val repository: TradingControlRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<TradingControlData>>(UiState.Loading)
    val uiState: StateFlow<UiState<TradingControlData>> = _uiState.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResultUiState?>(null)
    val actionResult: StateFlow<ActionResultUiState?> = _actionResult.asStateFlow()

    private val _pendingLiveMode = MutableStateFlow(false)
    val pendingLiveMode: StateFlow<Boolean> = _pendingLiveMode.asStateFlow()

    private var _startJob: Job? = null
    private var _stopJob: Job? = null
    private var _modeJob: Job? = null
    private var _depoJob: Job? = null
    private var _closeJob: Job? = null

    init {
        fetchState()
    }

    fun fetchState() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            // delay(1L) creates a real virtual-time suspension point so Turbine observes Loading
            // before the fetch result arrives (same technique as ScannerViewModel).
            delay(1L)
            when (val result = repository.fetchState()) {
                is TradingControlResult.Success -> {
                    _uiState.value = UiState.Ready(result.data)
                }
                is TradingControlResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = result.message ?: "Could not load trading state",
                    )
                }
            }
        }
    }

    fun start() {
        if (_startJob?.isActive == true) return
        _startJob = viewModelScope.launch {
            // delay(1L) creates a real suspension point so the double-tap guard (_startJob.isActive)
            // remains true when a second start() is called synchronously in the same frame.
            // With UnconfinedTestDispatcher in tests, delay() suspends until advanceUntilIdle(),
            // ensuring the job is still active when the second call arrives.
            delay(1L)
            when (val result = repository.start()) {
                is ActionResult.Success -> {
                    _actionResult.value = ActionResultUiState.Success
                    refreshState()
                }
                is ActionResult.Error -> {
                    _actionResult.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }

    fun stop() {
        if (_stopJob?.isActive == true) return
        _stopJob = viewModelScope.launch {
            // delay(1L): suspension point so the double-tap guard (_stopJob.isActive) holds
            // when a second stop() is called synchronously in the same frame (matches start()).
            delay(1L)
            when (val result = repository.stop()) {
                is ActionResult.Success -> {
                    _actionResult.value = ActionResultUiState.Success
                    refreshState()
                }
                is ActionResult.Error -> {
                    _actionResult.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }

    /**
     * Initiates a mode change. For [ExecutionMode.LIVE], sets [pendingLiveMode] to true and
     * waits for [confirmLiveMode]. For [ExecutionMode.PAPER], fires the write immediately.
     */
    fun setMode(mode: ExecutionMode) {
        when (mode) {
            ExecutionMode.LIVE -> _pendingLiveMode.value = true
            ExecutionMode.PAPER -> performSetMode("paper")
        }
    }

    /** Called when the user accepts the LIVE confirm dialog. Fires the write and clears the dialog. */
    fun confirmLiveMode() {
        _pendingLiveMode.value = false
        performSetMode("live")
    }

    /** Called when the user cancels the LIVE confirm dialog. No write is fired. */
    fun cancelLiveMode() {
        _pendingLiveMode.value = false
    }

    fun setDepoAlerts(enabled: Boolean) {
        if (_depoJob?.isActive == true) return
        _depoJob = viewModelScope.launch {
            // delay(1L): suspension point so the in-flight guard holds against rapid re-taps.
            delay(1L)
            when (val result = repository.setDepoAlerts(enabled)) {
                is ActionResult.Success -> {
                    _actionResult.value = ActionResultUiState.Success
                    refreshState()
                }
                is ActionResult.Error -> {
                    _actionResult.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }

    fun close(signalId: String) {
        if (_closeJob?.isActive == true) return
        _closeJob = viewModelScope.launch {
            // delay(1L): suspension point so the in-flight guard holds against rapid re-taps,
            // preventing duplicate close orders for the same position.
            delay(1L)
            when (val result = repository.close(signalId)) {
                is ActionResult.Success -> {
                    _actionResult.value = ActionResultUiState.Success
                    refreshState()
                }
                is ActionResult.Error -> {
                    _actionResult.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }

    /** Clears the one-shot action result (called after the snackbar is shown). */
    fun clearActionResult() {
        _actionResult.value = null
    }

    private fun performSetMode(modeString: String) {
        if (_modeJob?.isActive == true) return
        _modeJob = viewModelScope.launch {
            // delay(1L): suspension point so the in-flight guard holds against rapid re-taps.
            delay(1L)
            when (val result = repository.setMode(modeString)) {
                is ActionResult.Success -> {
                    _actionResult.value = ActionResultUiState.Success
                    refreshState()
                }
                is ActionResult.Error -> {
                    _actionResult.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }

    private suspend fun refreshState() {
        when (val result = repository.fetchState()) {
            is TradingControlResult.Success -> {
                _uiState.value = UiState.Ready(result.data)
            }
            is TradingControlResult.Error -> {
                // Keep the existing uiState on refresh failure — do not overwrite with error
                // so the screen remains usable after a write that succeeds but refresh fails.
            }
        }
    }
}
