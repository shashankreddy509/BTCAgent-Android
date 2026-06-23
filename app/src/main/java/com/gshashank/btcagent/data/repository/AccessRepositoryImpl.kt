package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.AccessApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps [AccessApi.checkAccess] to [AccessResult].
 *
 * MOBILE-27 — gate flag `gate_access_check_body` (Option A — local inversion):
 *   The flag is read from [catalogRepository] at the top of [checkAccess].
 *
 *   Flag ON  (catalogOn returns true)  → NEW body-based mapping:
 *     200 + allowed=true  → [AccessResult.Allowed] (carrying admin)
 *     200 + allowed=false → [AccessResult.Pending]
 *     200 + null body     → [AccessResult.Error] (cannot confirm — never fail open)
 *     401                 → [AccessResult.Unauthorized]
 *     anything else       → [AccessResult.Error] with null cause
 *     any thrown exception → [AccessResult.Error] with non-null cause
 *
 *   Flag OFF (catalogOn returns false) → OLD status-based mapping: 200→Allowed(admin=false),
 *                                        401→Unauthorized, else→Error.
 *
 *   OPTION-A CONTRACT: when the flag is MISSING from the catalog (catalog empty or key absent),
 *   this class treats it as ON — the SAFE/body-based path. This is achieved at the call site by
 *   using `catalogRepository.catalogOn("gate_access_check_body", default = true)` rather than the
 *   standard `catalogOn()` which returns false for absent keys.
 *
 *   Security-sensitive: a missing flag falls back to the SAFE body-based path, NOT the catalog
 *   default (false), so a first-launch catalog-fetch failure cannot bypass the gate. Flip the flag
 *   to false server-side for explicit rollback to the status-based path.
 *
 *   See PLAN.md §Risks/Option-A for the full rationale.
 */
@Singleton
class AccessRepositoryImpl @Inject constructor(
    private val accessApi: AccessApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val catalogRepository: CatalogRepository,
) : AccessRepository {

    override suspend fun checkAccess(): AccessResult = withContext(ioDispatcher) {
        try {
            val response = accessApi.checkAccess()

            // MOBILE-27 Option-A: Security-sensitive gate.
            // Using catalogOn("gate_access_check_body", default = true) means a MISSING key (e.g.
            // catalog fetch not yet succeeded on first launch) falls back to the SAFE body-based
            // path, NOT the old status-based path. The local default=true inverts the catalog-wide
            // default-false convention specifically for this security-critical flag.
            // Only an explicit false in the server-side catalog triggers the rollback/legacy path.
            val useNewBodyBasedPath = catalogRepository.catalogOn(
                flag = CatalogFlags.GATE_ACCESS_CHECK_BODY,
                default = true,
            )

            if (useNewBodyBasedPath) {
                // NEW path — verdict comes from the response body, not the status code.
                when {
                    response.code() == 200 -> {
                        val body = response.body()
                        when {
                            body == null -> AccessResult.Error(cause = null)
                            body.allowed -> AccessResult.Allowed(admin = body.admin)
                            else -> AccessResult.Pending
                        }
                    }
                    response.code() == 401 -> AccessResult.Unauthorized
                    else -> AccessResult.Error(cause = null)
                }
            } else {
                // OLD path — status-based mapping preserved for instant server-side rollback.
                // Flip `gate_access_check_body` to false in the catalog to activate this branch.
                when (response.code()) {
                    200 -> AccessResult.Allowed(admin = false)
                    401 -> AccessResult.Unauthorized
                    else -> AccessResult.Error(cause = null)
                }
            }
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            AccessResult.Error(cause = e)
        }
    }
}
