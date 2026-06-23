package com.gshashank.btcagent.data.repository

/**
 * Sealed result type returned by [AccessRepository.checkAccess].
 * Variants mirror the HTTP codes from the allow-list endpoint:
 *   200 → Allowed, 403 → Pending, 401 → Unauthorized, everything else → Error.
 */
sealed interface AccessResult {
    data object Allowed : AccessResult
    data object Pending : AccessResult
    data object Unauthorized : AccessResult
    data class Error(val cause: Throwable?) : AccessResult
}
