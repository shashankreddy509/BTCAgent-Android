package com.gshashank.btcagent.data.repository

/**
 * Provides access to runtime catalog feature flags.
 *
 * Flag keys are numeric platform+id integers (android = 1xxxxx, ios = 2xxxxx).
 * Flags are fetched from the versioned platform API and persisted in DataStore
 * for last-known-good behavior across restarts.
 */
interface CatalogRepository {
    /** Fetches the latest catalog from the server. NEVER throws; fail-open. */
    suspend fun refresh()

    /**
     * Returns the stored value for [id], or `false` if the id is absent or
     * no successful fetch has occurred yet.
     */
    fun isEnabled(id: Int): Boolean

    /**
     * Returns the stored value when [id] is explicitly present in the catalog
     * (even if the stored value is `false`). Returns [default] only when the
     * key is entirely absent from the fetched catalog.
     *
     * Option-A inversion for security-sensitive flags: pass `default = true` so
     * that a missing/failed fetch falls back to the SAFE path rather than OFF.
     * An explicit `false` from the server correctly overrides the default.
     */
    fun isEnabled(id: Int, default: Boolean): Boolean
}
