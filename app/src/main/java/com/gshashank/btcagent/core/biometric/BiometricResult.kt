package com.gshashank.btcagent.core.biometric

/**
 * Result of a biometric authentication attempt — MOBILE-19.
 *
 * The UI layer observes [ManualEntryViewModel.pendingConfirmState], launches the system biometric
 * prompt when it is non-null, and feeds the outcome back via
 * [ManualEntryViewModel.onBiometricResult]. The ViewModel then decides whether to POST the order
 * (Success only) or discard it (Cancelled / Failed).
 */
sealed class BiometricResult {
    data object Success : BiometricResult()
    data object Failed : BiometricResult()
    data object Cancelled : BiometricResult()
    data object Unavailable : BiometricResult()
}
