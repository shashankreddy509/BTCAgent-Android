package com.gshashank.btcagent.data.repository

/**
 * Contract for the runtime feature-flag registry ("catalog").
 *
 * The implementation fetches a flat `{ "flag_name": bool }` JSON map from the backend and caches
 * it in-memory. Callers read flags synchronously via [catalogOn].
 *
 * Invariant for all flags:
 *   - A flag present and true  → ON
 *   - A flag present and false → OFF
 *   - A flag absent from the map → OFF (false) — returned by [catalogOn(flag)]
 *
 * Exception — Option A gate-flag inversion (MOBILE-27):
 *   `gate_access_check_body` is treated as ON when MISSING at the [AccessRepositoryImpl] call
 *   site. That inversion is achieved by calling [catalogOn(flag, default)] with `default = true`.
 *   The two-arg overload returns the provided [default] when the key is absent, rather than
 *   the catalog-global default of `false`.
 *   See PLAN.md §Risks/Option-A for the full security rationale.
 */
interface CatalogRepository {

    /**
     * Fetches the flag map from the server and replaces the in-memory cache.
     *
     * Contract: NEVER throws to callers. On any error (network, HTTP, parse), the last-known map
     * is preserved. If no successful fetch has occurred yet, the cache remains empty and all
     * [catalogOn] calls return false.
     */
    suspend fun refresh()

    /**
     * Returns the current value of [flag] from the in-memory cache.
     *
     * This is a synchronous, non-blocking read. Returns `false` if [flag] is absent or if no
     * successful refresh has occurred yet.
     */
    fun catalogOn(flag: String): Boolean

    /**
     * Returns the current value of [flag] from the in-memory cache, using [default] when the
     * key is entirely ABSENT (distinct from a flag present-and-false).
     *
     * Abstract on purpose: a delegating default body would silently drop [default] (return false
     * for absent keys), which would break the security-sensitive Option-A path. Every
     * implementation MUST distinguish "explicitly false" from "absent" (e.g. `getOrDefault`).
     *
     * Option-A usage: security-sensitive callers pass `default = true` so a missing key falls
     * back to the SAFE path rather than the legacy path. See [AccessRepositoryImpl].
     */
    fun catalogOn(flag: String, default: Boolean): Boolean
}
