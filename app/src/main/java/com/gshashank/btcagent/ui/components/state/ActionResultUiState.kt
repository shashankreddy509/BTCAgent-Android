package com.gshashank.btcagent.ui.components.state

/**
 * Result of a one-shot write action (e.g. close position, edit TP/SL, trigger scan) exposed
 * to the UI layer. Canonical cross-feature type — shared by Positions (MOBILE-6) and
 * Scanner (MOBILE-8). A `null` value means "no action result to surface".
 */
sealed class ActionResultUiState {
    data object Success : ActionResultUiState()
    data class Error(val code: Int, val message: String) : ActionResultUiState()
}
