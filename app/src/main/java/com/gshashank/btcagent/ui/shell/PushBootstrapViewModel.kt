package com.gshashank.btcagent.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gshashank.btcagent.data.network.FcmTokenProvider
import com.gshashank.btcagent.data.repository.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bootstraps + tears down the FCM push token around the Gate/sign-out boundary — MOBILE-41.
 *
 * [onNotificationPermissionGranted] is called by [AppShell] after the Android 13+
 * POST_NOTIFICATIONS runtime permission is granted (or is already granted / not required below
 * API 33). It fetches the current FCM token and registers it with the backend.
 *
 * The sign-out UNREGISTER path lives in
 * [com.gshashank.btcagent.ui.settings.SettingsViewModel.signOut] instead of here — it must run
 * BEFORE the Firebase session is torn down so the DELETE is authenticated, which the app-shell
 * layer can't guarantee ordering for.
 *
 * The register path is fire-and-forget: there is no UI surface for this at the app-shell layer,
 * and [NotificationsRepository.register] never throws — a 404 (global push toggle OFF) is already
 * mapped to an inert result upstream, so there is nothing actionable to show here.
 */
@HiltViewModel
class PushBootstrapViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    private val fcmTokenProvider: FcmTokenProvider,
) : ViewModel() {

    fun onNotificationPermissionGranted() {
        viewModelScope.launch {
            fcmTokenProvider.currentToken()?.let { token ->
                notificationsRepository.register(token)
            }
        }
    }
}
