package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.DashboardData

sealed class DashboardResult {
    data class Success(val data: DashboardData) : DashboardResult()
    data class Error(val cause: Throwable? = null) : DashboardResult()
}
