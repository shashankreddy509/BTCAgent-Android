package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ScanDirection
import com.gshashank.btcagent.data.model.ScanSignal
import com.gshashank.btcagent.data.model.ScannerData
import com.gshashank.btcagent.data.network.ScannerApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [ScannerRepository] — MOBILE-8.
 *
 * Calls [ScannerApi.getScan] and maps the DTO to [ScannerData], deriving [ScanDirection]
 * from the pattern name:
 *   "Morning Star"  → [ScanDirection.Bullish]
 *   "Evening Star"  → [ScanDirection.Bearish]
 *   "4-Flag" / any → [ScanDirection.Neutral]
 *
 * NEVER throws to callers — all exceptions are caught and returned as [ScannerResult.Error].
 * [CancellationException] is rethrown so coroutine cancellation propagates correctly.
 */
@Singleton
class ScannerRepositoryImpl @Inject constructor(
    private val scannerApi: ScannerApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScannerRepository {

    override suspend fun fetchScan(): ScannerResult = withContext(ioDispatcher) {
        try {
            val response = scannerApi.getScan()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext ScannerResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: return@withContext ScannerResult.Error(message = "Empty response body")

            val signals = body.results.mapNotNull { dto ->
                val timeframe = dto.tf ?: return@mapNotNull null
                val pattern = dto.pattern ?: return@mapNotNull null
                val barsAgo = dto.barsAgo ?: return@mapNotNull null
                val openPrice = dto.barOpenPrice ?: return@mapNotNull null

                ScanSignal(
                    timeframe = timeframe,
                    pattern = pattern,
                    barsAgo = barsAgo,
                    openPrice = openPrice,
                    depoLine = dto.depoLine,
                    direction = deriveDirection(pattern),
                )
            }

            ScannerResult.Success(
                ScannerData(
                    timestamp = body.timestamp,
                    signals = signals,
                )
            )
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            ScannerResult.Error(message = e.message)
        }
    }

    override suspend fun triggerScan(): ActionResult = withContext(ioDispatcher) {
        try {
            val response = scannerApi.triggerScan()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext ActionResult.Error(
                    code = response.code(),
                    message = "HTTP ${response.code()}",
                )
            }
            val body = response.body()
                ?: return@withContext ActionResult.Error(code = -1, message = "Empty response body")

            return@withContext when (body.status) {
                "started", "already_running" -> ActionResult.Success
                else -> ActionResult.Error(code = -1, message = "Unknown status: ${body.status}")
            }
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = e.message ?: "Unknown error")
        }
    }

    /**
     * Derives the [ScanDirection] from the pattern name.
     * The backend has no direction field — this is the client-side discriminator.
     *
     * "Morning Star" → Bullish, "Evening Star" → Bearish, everything else → Neutral.
     */
    internal fun deriveDirection(pattern: String): ScanDirection = when (pattern) {
        "Morning Star" -> ScanDirection.Bullish
        "Evening Star" -> ScanDirection.Bearish
        else -> ScanDirection.Neutral
    }
}
