package com.gshashank.btcagent.data.repository

/**
 * Contract for checking whether the signed-in user is on the allow-list.
 */
interface AccessRepository {
    suspend fun checkAccess(): AccessResult
}
