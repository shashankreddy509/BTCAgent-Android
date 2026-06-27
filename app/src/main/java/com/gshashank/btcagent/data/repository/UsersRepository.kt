package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ExecutionMode

/**
 * Contract for the admin Users feature — MOBILE-21.
 *
 * All methods return sealed result types; implementations NEVER throw to callers.
 */
interface UsersRepository {

    /** Fetches all users and the allowlist, partitioning them into pending/active lists. */
    suspend fun fetchUsers(): AdminUsersResult

    /**
     * Approves [email] by reading the current allowlist, adding [email], and PUTting the full list.
     * Re-fetches state after a successful write.
     */
    suspend fun approve(email: String): ActionResult

    /**
     * Rejects [email] by reading the current allowlist, removing [email], and PUTting the full list.
     * Re-fetches state after a successful write.
     */
    suspend fun reject(email: String): ActionResult

    /** Sets the execution mode for the user identified by [uid]. */
    suspend fun setMode(uid: String, mode: ExecutionMode): ActionResult

    /** Stops the scanner for the user identified by [uid]. */
    suspend fun stop(uid: String): ActionResult
}
