package com.gshashank.btcagent.data.network

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable seam over the current FCM device token — MOBILE-41.
 *
 * Wraps [FirebaseMessaging.getInstance] so callers ([com.gshashank.btcagent.ui.settings.SettingsViewModel]
 * on sign-out, [com.gshashank.btcagent.ui.shell.PushBootstrapViewModel] on permission-grant) can be
 * unit-tested with a fake — the hardcoded static call is confined here.
 *
 * Returns null when no token is available (e.g. Play Services missing / FCM not ready) instead of
 * throwing, so callers can no-op cleanly.
 */
interface FcmTokenProvider {
    /** Returns the current FCM token, or null when none is available. Must not throw (except to
     *  propagate coroutine [CancellationException], which callers rely on for structured concurrency). */
    suspend fun currentToken(): String?
}

@Singleton
class FirebaseFcmTokenProvider @Inject constructor() : FcmTokenProvider {
    override suspend fun currentToken(): String? =
        runCatching { FirebaseMessaging.getInstance().token.await() }
            // Never swallow cancellation — rethrow so the enclosing coroutine unwinds properly
            // (matches NotificationsRepositoryImpl's convention). Any other failure → null.
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
}
