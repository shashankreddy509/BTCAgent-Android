package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ScannerData

sealed class ScannerResult {
    data class Success(val data: ScannerData) : ScannerResult()
    data class Error(val message: String? = null) : ScannerResult()
}
