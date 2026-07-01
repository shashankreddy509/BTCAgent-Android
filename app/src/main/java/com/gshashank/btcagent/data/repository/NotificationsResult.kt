package com.gshashank.btcagent.data.repository

/**
 * Result type returned by [NotificationsRepository] — MOBILE-41.
 *
 * [Inert] is returned when the backend's global push_notifications toggle is OFF (HTTP 404) —
 * this is NOT an error: the repository must never surface it as an error to the UI, since prod
 * has the toggle OFF until validated and the app must stay silent/functional in that state.
 */
sealed class NotificationsResult {
    data object Success : NotificationsResult()
    data object Inert : NotificationsResult()
    data class Error(val code: Int = -1, val message: String? = null) : NotificationsResult()
}
