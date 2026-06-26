package com.gshashank.btcagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.UserSettings
import com.gshashank.btcagent.data.repository.ActionResult
import com.gshashank.btcagent.data.repository.AppearanceRepository
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.data.repository.SettingsRepository
import com.gshashank.btcagent.data.repository.SettingsResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen — MOBILE-20.
 *
 * Exposes:
 * - [uiState] — Loading → Ready/Error after fetch.
 * - [actionResult] — nullable one-shot write feedback (snackbar).
 * - [navigateToLogin] — emits Unit when sign-out completes.
 *
 * Double-tap guard: [saveJob] prevents concurrent in-flight save calls.
 * Validation errors (qty) are surfaced immediately via actionResult without an HTTP call.
 * Save success triggers a state refresh (loadSettings).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appearanceRepository: AppearanceRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<UserSettings>>(UiState.Loading)
    val uiState: StateFlow<UiState<UserSettings>> = _uiState.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResultUiState?>(null)
    val actionResult: StateFlow<ActionResultUiState?> = _actionResult.asStateFlow()

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

    /** Persisted dark-mode preference, so the Appearance toggle reflects the stored state. */
    val darkMode: StateFlow<Boolean> = appearanceRepository.darkModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    private var saveJob: Job? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(1L)
            when (val result = settingsRepository.fetchUserSettings()) {
                is SettingsResult.Success -> {
                    _uiState.value = UiState.Ready(result.settings)
                }
                is SettingsResult.Error -> {
                    _uiState.value = UiState.Error(
                        code = "ERR_FETCH",
                        message = result.message,
                    )
                }
            }
        }
    }

    fun saveTradingParams(
        qty: Int?,
        maxSl: Double?,
        minTp: Double?,
        maxConcurrent: Int?,
        mode: ExecutionMode?,
    ) {
        // Client-side qty validation: surface error immediately without touching the repo.
        if (qty != null && (qty <= 0 || qty > 1000 || qty % 2 != 0)) {
            _actionResult.value = ActionResultUiState.Error(
                code = 422,
                message = "Invalid qty: must be even and 0 < qty <= 1000",
            )
            return
        }

        // Double-tap guard: ignore second tap while a save is in-flight.
        if (saveJob?.isActive == true) return

        saveJob = viewModelScope.launch {
            delay(1L)
            when (val result = settingsRepository.saveTradingParams(qty, maxSl, minTp, maxConcurrent, mode)) {
                is ActionResult.Success -> {
                    _actionResult.value = ActionResultUiState.Success
                    loadSettings()
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

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            appearanceRepository.setDarkMode(enabled)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _navigateToLogin.emit(Unit)
        }
    }
}
