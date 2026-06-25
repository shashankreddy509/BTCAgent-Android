package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.RegimeData

/**
 * Result type returned by [RegimeRepository.fetchRegime] — MOBILE-12.
 */
sealed class RegimeResult {
    data class Success(val data: RegimeData) : RegimeResult()
    data class Error(val message: String? = null) : RegimeResult()
}
