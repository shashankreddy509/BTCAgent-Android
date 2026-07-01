package com.gshashank.btcagent.data.repository

/**
 * Repository interface for FCM push registration — MOBILE-41.
 *
 * Implementations MUST NOT throw to callers, and MUST treat a 404 (global push toggle OFF)
 * as [NotificationsResult.Inert], not [NotificationsResult.Error].
 */
interface NotificationsRepository {

    /** Registers the device's FCM token with the backend. */
    suspend fun register(fcmToken: String): NotificationsResult

    /** Unregisters the device's FCM token (called on sign-out). */
    suspend fun unregister(fcmToken: String): NotificationsResult
}
