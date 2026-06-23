package com.gshashank.btcagent.data.repository

/**
 * Sealed result returned by [AccessRepository.checkAccess].
 *
 * The endpoint returns 200 + `{allowed, admin}` for any authenticated user, so the
 * verdict comes from the body, not the status code:
 *   200 allowed=true  → Allowed(admin), 200 allowed=false → Pending,
 *   401 → Unauthorized, everything else / exception → Error.
 */
sealed interface AccessResult {
    data class Allowed(val admin: Boolean) : AccessResult
    data object Pending : AccessResult
    data object Unauthorized : AccessResult
    data class Error(val cause: Throwable?) : AccessResult
}
