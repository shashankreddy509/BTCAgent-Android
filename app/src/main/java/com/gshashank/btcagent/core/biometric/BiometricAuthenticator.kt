package com.gshashank.btcagent.core.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Stateless wrapper around [BiometricPrompt] — MOBILE-19.
 *
 * Bridges the callback-based [BiometricPrompt] API to a suspend function. Runs the prompt on the
 * main thread via [ContextCompat.getMainExecutor]. Returns a [BiometricResult] without throwing.
 *
 * Allowed authenticators: BIOMETRIC_STRONG or DEVICE_CREDENTIAL so a PIN/passcode fallback works
 * when no biometric is enrolled. Per the API contract, a negative button must NOT be set when
 * DEVICE_CREDENTIAL is in the allowed-authenticators mask.
 */
class BiometricAuthenticator {

    /**
     * Shows the system biometric prompt and suspends until the user authenticates, cancels,
     * or a terminal error occurs.
     *
     * @param activity the host [FragmentActivity] required by [BiometricPrompt].
     * @param title the title shown in the prompt dialog.
     * @param subtitle the subtitle shown in the prompt dialog.
     * @return [BiometricResult.Success] on successful authentication;
     *         [BiometricResult.Unavailable] when the device has no usable authenticator;
     *         [BiometricResult.Cancelled] for user cancellation, timeout, or lockout.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): BiometricResult {
        val allowedAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        val manager = BiometricManager.from(activity)
        if (manager.canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricResult.Unavailable
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Terminal: user cancelled, hardware lockout, timeout, etc.
                    if (continuation.isActive) continuation.resume(BiometricResult.Cancelled)
                }

                override fun onAuthenticationFailed() {
                    // Non-terminal: bad read (fingerprint not recognised). Prompt stays up.
                    // Do NOT resume here — let the user try again.
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                // DEVICE_CREDENTIAL is in the allowed mask — negative button must NOT be set (API requirement).
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()

            continuation.invokeOnCancellation {
                prompt.cancelAuthentication()
            }

            prompt.authenticate(promptInfo)
        }
    }
}
