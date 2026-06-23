package com.gshashank.btcagent.ui.auth

import com.google.firebase.auth.FirebaseUser

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: FirebaseUser) : LoginUiState
    data class Error(val message: String) : LoginUiState
}
