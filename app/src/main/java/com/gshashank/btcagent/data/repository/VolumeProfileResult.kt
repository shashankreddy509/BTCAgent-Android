package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.VolumeProfileData

/**
 * Result type returned by [VolumeProfileRepository.fetch] — MOBILE-14.
 */
sealed class VolumeProfileResult {
    data class Success(val data: VolumeProfileData) : VolumeProfileResult()
    data class Error(val message: String? = null) : VolumeProfileResult()
}
