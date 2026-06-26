package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.AnalyticsData

sealed class AnalyticsResult {
    data class Success(val data: AnalyticsData) : AnalyticsResult()
    data class Error(val message: String? = null) : AnalyticsResult()
}
