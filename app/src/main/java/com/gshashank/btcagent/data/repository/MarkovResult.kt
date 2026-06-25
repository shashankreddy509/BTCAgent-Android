package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.MarkovData

/**
 * Result type returned by [MarkovRepository.fetchTickers] — MOBILE-13.
 */
sealed class MarkovResult {
    data class Success(val data: MarkovData) : MarkovResult()
    data class Error(val message: String? = null) : MarkovResult()
}
