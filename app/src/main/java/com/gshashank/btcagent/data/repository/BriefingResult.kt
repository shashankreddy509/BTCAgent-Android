package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.BriefingData

/**
 * Result type returned by [BriefingRepository.fetchBriefing] — MOBILE-9.
 */
sealed class BriefingResult {
    data class Success(val data: BriefingData) : BriefingResult()
    data class Error(val message: String? = null) : BriefingResult()
}
