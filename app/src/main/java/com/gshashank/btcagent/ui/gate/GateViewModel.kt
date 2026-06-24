package com.gshashank.btcagent.ui.gate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.repository.AccessRepository
import com.gshashank.btcagent.data.repository.AccessResult
import com.gshashank.btcagent.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Gate screen.
 *
 * Checks whether the signed-in user is on the allow-list and exposes the result as a
 * [StateFlow<GateUiState>]. Uses [viewModelScope] (auto-cancelled when the ViewModel is cleared)
 * so no manual scope management is needed.
 *
 * [delay] (1 ms of virtual time) after setting [GateUiState.Loading] creates a genuine
 * suspension point that defers the repository call until [kotlinx.coroutines.test.runTest]
 * advances its virtual clock — ensuring Turbine can observe the Loading state before the
 * terminal state arrives. A simple [kotlinx.coroutines.yield] is insufficient because
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] resumes it immediately, conflating
 * Loading away before any subscriber can observe it.
 */
@HiltViewModel
class GateViewModel @Inject constructor(
    private val accessRepository: AccessRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GateUiState>(GateUiState.Loading)
    val uiState: StateFlow<GateUiState> = _uiState.asStateFlow()

    /** Tracks the in-flight access check so retry/sign-out can cancel a stale request. */
    private var accessCheckJob: Job? = null

    init {
        checkAccess()
    }

    private fun checkAccess() {
        accessCheckJob?.cancel()
        accessCheckJob = viewModelScope.launch {
            _uiState.value = GateUiState.Loading
            // delay(1L) creates a real virtual-time suspension point so that runTest can
            // advance its clock and allow Turbine to observe the Loading state before the
            // repository call (and its result) arrives. yield() alone is not sufficient with
            // UnconfinedTestDispatcher because that dispatcher resumes immediately after a
            // yield, conflating Loading with the terminal state.
            delay(1L)
            val result = accessRepository.checkAccess()
            _uiState.value = when (result) {
                is AccessResult.Allowed -> GateUiState.Allowed
                AccessResult.Pending -> GateUiState.Pending(
                    email = authRepository.currentUser?.email ?: ""
                )
                AccessResult.Unauthorized -> GateUiState.Unauthorized
                is AccessResult.Error -> {
                    Log.e(TAG, "Access check failed", result.cause)
                    GateUiState.Error
                }
            }
        }
    }

    fun onRetry() {
        checkAccess()
    }

    fun onSignOut() {
        // Cancel any in-flight access check so a late 200 cannot overwrite Unauthorized.
        accessCheckJob?.cancel()
        authRepository.signOut()
        _uiState.value = GateUiState.Unauthorized
    }

    private companion object {
        const val TAG = "GateViewModel"
    }
}
