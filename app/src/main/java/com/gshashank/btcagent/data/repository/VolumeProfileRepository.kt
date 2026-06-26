package com.gshashank.btcagent.data.repository

/**
 * Repository interface for the Volume Profile data source — MOBILE-14.
 */
interface VolumeProfileRepository {
    suspend fun fetch(): VolumeProfileResult
}
