package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ReportsData

/**
 * Result type returned by [ReportsRepository.fetchReports] — MOBILE-7.
 */
sealed class ReportsResult {
    data class Success(val data: ReportsData) : ReportsResult()
    data class Error(val message: String? = null) : ReportsResult()
}
