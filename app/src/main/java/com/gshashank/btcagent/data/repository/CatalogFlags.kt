package com.gshashank.btcagent.data.repository

/**
 * Runtime catalog feature-flag identifiers.
 * Numeric ids: android = 1xxxxx (platform 1), ios = 2xxxxx (platform 2).
 */
object CatalogFlags {
    /**
     * login_mock — android id 100001, ios id 200001.
     * Verified live at catalog version 1: ON.
     * Use: `catalogRepository.isEnabled(LOGIN_MOCK)`.
     */
    const val LOGIN_MOCK = 100001

    /**
     * user_access_status (backend Firestore key: gate_access_check_body) — android id 100002,
     * ios id 200002. Allocated via BTCWEB-16 (Done), verified live at catalog version 4: ON.
     * Gates the access-check verdict mapping (MOBILE-27, Option-A). Security-sensitive:
     * read with `isEnabled(USER_ACCESS_STATUS, default = true)` so a missing key falls back to
     * the SAFE body-based path. An explicit server-side `false` triggers the legacy rollback path.
     */
    const val USER_ACCESS_STATUS = 100002

    /** markov_matrix — android 100003 / iOS 200003.
     *  ON  = Markets hub Markov Matrix tile + screen visible.
     *  OFF/absent = tile hidden (default=false at isEnabledFlow call site; not security-sensitive).
     *  Allocated via BTCWEB-22; live catalog v5, seq 3. */
    const val MARKOV_MATRIX = 100003
}
