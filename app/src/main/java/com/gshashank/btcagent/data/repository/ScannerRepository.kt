package com.gshashank.btcagent.data.repository

interface ScannerRepository {
    suspend fun fetchScan(): ScannerResult
    suspend fun triggerScan(): ActionResult
}
