package com.gshashank.btcagent.data.repository

/**
 * Catalog (feature-flag) names. One const per flag so call sites can't typo a name —
 * a mistyped literal silently reads the wrong/absent flag.
 */
object CatalogFlags {
    /**
     * Gates the access-check verdict path. ON/missing → body-based mapping (safe);
     * explicit false → legacy status-based mapping (rollback). See [AccessRepositoryImpl].
     */
    const val GATE_ACCESS_CHECK_BODY = "gate_access_check_body"
}
