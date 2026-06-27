package com.gshashank.btcagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.repository.AppearanceRepository
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Entry-point ViewModel: decides the nav start destination from the onboarding seen-flag
 * (MOBILE-23) and the persisted auth session (MOBILE-33).
 *
 * [startDestination] is a [StateFlow]<[Route]?> whose initial value is null (unresolved).
 * An [init] coroutine reads both the DataStore flag and the Firebase session asynchronously,
 * then emits the resolved route. MainActivity must render a placeholder until non-null.
 *
 * Priority rule:
 *   - hasSeenOnboarding == false  → [Route.Onboarding]  (regardless of session)
 *   - hasSeenOnboarding == true  + session present → [Route.Gate]
 *   - hasSeenOnboarding == true  + no session      → [Route.Login]
 *
 * DataStore I/O is guarded with try/catch: if the read fails (corrupt file, I/O error), the
 * safe fallback is false (show onboarding), which is always safe to display.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appearanceRepository: AppearanceRepository,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<Route?>(null)
    val startDestination: StateFlow<Route?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            // Resolve the start destination from the onboarding flag + auth session. BOTH reads are
            // guarded: any failure must still produce a non-null Route so the NavHost can render —
            // never leave _startDestination null (that would be a permanent blank screen).
            val destination = try {
                val seen = appearanceRepository.hasSeenOnboardingFlow.first()
                val user = authRepository.currentUser
                when {
                    !seen -> Route.Onboarding
                    user != null -> Route.Gate
                    else -> Route.Login
                }
            } catch (e: Exception) {
                // Safe fallback on any read failure: show onboarding (always safe to display).
                Route.Onboarding
            }
            _startDestination.value = destination
        }
    }

    /**
     * Persists the onboarding seen-flag. Fire-and-forget in [viewModelScope].
     *
     * Note: on extremely rapid ViewModel teardown the coroutine may be cancelled before the
     * DataStore flush completes. In that case onboarding is shown once more on the next cold
     * launch — acceptable for a one-time first-launch UX screen.
     */
    fun markOnboardingSeen() {
        viewModelScope.launch {
            appearanceRepository.setHasSeenOnboarding(true)
        }
    }
}
