package com.gshashank.btcagent.ui.positions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessResult
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.PositionsRepository
import com.gshashank.btcagent.data.repository.PositionsResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Position Detail screen — MOBILE-6.
 *
 * Loads the target position by filtering [PositionsRepository.fetchPositions] by [signalId].
 * Exposes [canEdit] (derived from [AccessRepository.checkAccess]) and [actionState] for
 * close / editTpSl operations.
 *
 * Uses [AssistedInject] with [@Assisted] for [signalId] so the signal ID can be supplied
 * at creation time (not available at Hilt graph construction). Construct directly in unit
 * tests; use [Factory] in Compose via [hiltViewModel].
 */
@HiltViewModel(assistedFactory = PositionDetailViewModel.Factory::class)
class PositionDetailViewModel @AssistedInject constructor(
    @Assisted val signalId: String,
    private val repository: PositionsRepository,
    private val accessRepository: AccessRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(signalId: String): PositionDetailViewModel
    }

    private val _uiState = MutableStateFlow<UiState<Position>>(UiState.Loading)
    val uiState: StateFlow<UiState<Position>> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<ActionResultUiState?>(null)
    val actionState: StateFlow<ActionResultUiState?> = _actionState.asStateFlow()

    /**
     * True when the signed-in user has admin rights. Set asynchronously on init from
     * [AccessRepository.checkAccess]. MUST be a StateFlow (not a plain var): the access check
     * resolves AFTER first composition, and Compose only recomposes the Edit button when it
     * collects a StateFlow — a plain var would leave the button permanently hidden for admins.
     */
    private val _canEdit = MutableStateFlow(false)
    val canEdit: StateFlow<Boolean> = _canEdit.asStateFlow()

    /** Tracks the in-flight fetch so a post-action refresh cancels a stale fetch (no _uiState race). */
    private var fetchJob: Job? = null

    /** Tracks the in-flight close so a double-tap can't fire two concurrent cancel requests. */
    private var closeJob: Job? = null

    init {
        viewModelScope.launch {
            val accessResult = accessRepository.checkAccess()
            _canEdit.value = accessResult is AccessResult.Allowed && accessResult.admin
        }
        doFetch()
    }

    private fun doFetch() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            // delay(1L) creates a virtual-time suspension point so Turbine observes Loading
            // before the result arrives (same technique as DashboardViewModel / MOBILE-32).
            delay(1L)
            when (val result = repository.fetchPositions()) {
                is PositionsResult.Success -> {
                    val position = result.positions.firstOrNull { it.signalId == signalId }
                    if (position != null) {
                        _uiState.value = UiState.Ready(position)
                    } else {
                        _uiState.value = UiState.Error(
                            code = "ERR_NOT_FOUND",
                            message = "Position not found: $signalId",
                        )
                    }
                }
                is PositionsResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = result.message ?: "Could not load position",
                    )
                }
            }
        }
    }

    /** Closes (cancels) the current position and refreshes on success. */
    fun close() {
        // Ignore a re-tap while a close is already in flight (no double cancel request).
        if (closeJob?.isActive == true) return
        closeJob = viewModelScope.launch {
            when (val result = repository.close(signalId)) {
                is ActionResult.Success -> {
                    _actionState.value = ActionResultUiState.Success
                    doFetch()
                }
                is ActionResult.Error -> {
                    _actionState.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }

    /** Edits TP/SL for the current position and refreshes on success. Admin-gated. */
    fun editTpSl(sl: Double?, tp: Double?) {
        // Defense-in-depth: refuse locally if not admin (the server also returns 403). Avoids a
        // pointless call and surfaces a clear message if the UI ever shows the action in error.
        if (!_canEdit.value) {
            _actionState.value = ActionResultUiState.Error(403, "Not authorized to edit TP/SL")
            return
        }
        viewModelScope.launch {
            when (val result = repository.editTpSl(signalId, sl, tp)) {
                is ActionResult.Success -> {
                    _actionState.value = ActionResultUiState.Success
                    doFetch()
                }
                is ActionResult.Error -> {
                    _actionState.value = ActionResultUiState.Error(
                        code = result.code,
                        message = result.message,
                    )
                }
            }
        }
    }
}
