package com.gshashank.btcagent.ui.gate

/**
 * Sealed UI state for the Gate screen, mirroring the LoginUiState convention.
 * Variants:
 *   Loading       — initial state; spinner shown.
 *   Allowed       — user is on the allow-list; GateScreen navigates to Home.
 *   Pending(email)— user is not yet approved; pending-approval screen shown.
 *   Unauthorized  — no valid session; GateScreen navigates back to Login.
 *   Error         — unexpected failure; retry button shown.
 */
sealed interface GateUiState {
    data object Loading : GateUiState
    data object Allowed : GateUiState
    data class Pending(val email: String) : GateUiState
    data object Unauthorized : GateUiState
    data object Error : GateUiState
}
