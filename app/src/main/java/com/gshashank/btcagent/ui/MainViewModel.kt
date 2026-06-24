package com.gshashank.btcagent.ui

import androidx.lifecycle.ViewModel
import com.gshashank.btcagent.data.repository.AuthRepository
import com.gshashank.btcagent.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Entry-point ViewModel: decides the nav start destination from the PERSISTED auth session
 * (MOBILE-33). FirebaseAuth persists the signed-in user across cold starts; the launch routing
 * must consult it instead of always opening Login.
 *
 * Computed once at construction (cold-start launch decision): if a session already exists, start
 * at [Route.Gate] (which re-checks access and routes on to Home/Pending/Unauthorized); otherwise
 * start at [Route.Login]. A captured value is correct here precisely because this is the one-shot
 * launch decision — later sign-in/sign-out navigate explicitly via the existing callbacks.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    val startDestination: Route =
        if (authRepository.currentUser != null) Route.Gate else Route.Login
}
