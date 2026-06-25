package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.OpenInterestData

/**
 * Result type returned by [OpenInterestRepository.fetchOpenInterest] — MOBILE-11.
 */
sealed class OpenInterestResult {
    data class Success(val data: OpenInterestData) : OpenInterestResult()
    data class Error(val message: String? = null) : OpenInterestResult()
}
