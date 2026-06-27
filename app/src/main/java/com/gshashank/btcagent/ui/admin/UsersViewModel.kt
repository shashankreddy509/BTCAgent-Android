package com.gshashank.btcagent.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.model.AdminUsersData
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.AdminUsersResult
import com.gshashank.btcagent.data.repository.UsersRepository
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
 * ViewModel for the admin Users screen — MOBILE-21.
 *
 * Exposes:
 * - [uiState] — Loading → Ready(AdminUsersData) / Error after fetch.
 * - [actionResult] — nullable ActionResultUiState for one-shot write feedback (snackbar).
 *
 * Each write action (approve, reject, setMode, stop) has an in-flight Job guard and a
 * delay(1L) suspension point so the double-tap guard remains active in tests when a second
 * call arrives synchronously in the same frame.
 * All write successes trigger a state re-fetch.
 *
 * [refresh] also uses delay(1L) so that UiState.Loading is observable as the initial emission
 * even when the dispatcher runs coroutines eagerly (UnconfinedTestDispatcher in tests).
 */
@HiltViewModel
class UsersViewModel @Inject constructor(
    private val repository: UsersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AdminUsersData>>(UiState.Loading)
    val uiState: StateFlow<UiState<AdminUsersData>> = _uiState.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResultUiState?>(null)
    val actionResult: StateFlow<ActionResultUiState?> = _actionResult.asStateFlow()

    // Single shared action job: approve/reject/setMode/stop can't run concurrently (W9) — they all
    // mutate the allowlist or a user's state and each refreshes, so serialize them.
    private var _actionJob: Job? = null
    private var _refreshJob: Job? = null

    // NOTE: no init{} fetch. The admin endpoints must not be hit until the caller has confirmed the
    // current user is an admin — UsersScreen drives refresh(isAdmin) via LaunchedEffect(isAdmin) (C1).

    /**
     * Loads the user lists. Caller passes the resolved admin flag; when [isAdmin] is false this is a
     * no-op so a non-admin navigating directly to the route never fires the admin GET endpoints.
     */
    fun refresh(isAdmin: Boolean) {
        if (!isAdmin) return
        _refreshJob?.cancel()
        _refreshJob = viewModelScope.launch {
            // delay(1L) ensures UiState.Loading is observable as the first emission even when
            // the dispatcher runs coroutines eagerly (e.g. UnconfinedTestDispatcher in tests).
            delay(1L)
            when (val result = repository.fetchUsers()) {
                is AdminUsersResult.Success -> _uiState.value = UiState.Ready(result.data)
                is AdminUsersResult.Error -> _uiState.value = UiState.Error(
                    code = "ERR_FETCH",
                    message = result.message.ifEmpty { "Could not load users" },
                )
            }
        }
    }

    fun approve(email: String) = runAction { repository.approve(email) }

    fun reject(email: String) = runAction { repository.reject(email) }

    fun setMode(uid: String, mode: ExecutionMode) = runAction { repository.setMode(uid, mode) }

    fun stop(uid: String) = runAction { repository.stop(uid) }

    /**
     * Runs one admin write action behind a single shared in-flight guard. On success emits the
     * snackbar result and re-fetches the lists; on error surfaces the (masked) message.
     */
    private fun runAction(action: suspend () -> ActionResult) {
        if (_actionJob?.isActive == true) return
        _actionJob = viewModelScope.launch {
            // delay(1L): real suspension point so the in-flight guard holds against a second tap
            // arriving synchronously in the same frame.
            delay(1L)
            when (val result = action()) {
                is ActionResult.Success -> {
                    _actionResult.value = ActionResultUiState.Success
                    // The action only runs for an admin, so refreshing with isAdmin=true is safe.
                    refresh(isAdmin = true)
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

    /** Resets the one-shot action result (call after the snackbar is dismissed). */
    fun clearActionResult() {
        _actionResult.value = null
    }
}
