package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.NotificationsApi
import com.gshashank.btcagent.data.network.RegisterRequest
import com.gshashank.btcagent.data.network.UnregisterRequest
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [NotificationsRepository] — MOBILE-41.
 *
 * Never throws to callers (except [CancellationException], rethrown so coroutine cancellation
 * propagates). HTTP 404 means the backend's global `push_notifications` toggle is OFF (prod OFF,
 * dev ON) — that is mapped to [NotificationsResult.Inert], NOT an error, so the UI stays silent
 * for this expected prod state.
 */
@Singleton
class NotificationsRepositoryImpl @Inject constructor(
    private val notificationsApi: NotificationsApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NotificationsRepository {

    override suspend fun register(fcmToken: String): NotificationsResult = withContext(ioDispatcher) {
        try {
            val response = notificationsApi.register(RegisterRequest(fcmToken = fcmToken, platform = "android"))
            if (response.isSuccessful) {
                NotificationsResult.Success
            } else {
                response.errorBody()?.close()
                mapErrorCode(response.code())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NotificationsResult.Error(code = -1, message = "Failed to register for push notifications")
        }
    }

    override suspend fun unregister(fcmToken: String): NotificationsResult = withContext(ioDispatcher) {
        try {
            val response = notificationsApi.unregister(UnregisterRequest(fcmToken = fcmToken))
            if (response.isSuccessful) {
                NotificationsResult.Success
            } else {
                response.errorBody()?.close()
                mapErrorCode(response.code())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NotificationsResult.Error(code = -1, message = "Failed to unregister from push notifications")
        }
    }

    /** HTTP 404 means the global push toggle is OFF — inert, not an error. */
    private fun mapErrorCode(code: Int): NotificationsResult = if (code == 404) {
        NotificationsResult.Inert
    } else {
        NotificationsResult.Error(code = code, message = "Server error ($code)")
    }
}
