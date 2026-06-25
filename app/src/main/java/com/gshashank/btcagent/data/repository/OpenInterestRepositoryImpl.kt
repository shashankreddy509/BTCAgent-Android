package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.OiSignal
import com.gshashank.btcagent.data.model.OpenInterestData
import com.gshashank.btcagent.data.model.parseOiSignal
import com.gshashank.btcagent.data.network.OpenInterestApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [OpenInterestRepository] — MOBILE-11.
 *
 * Calls [OpenInterestApi.getOiNative] and maps the DTO to domain models:
 *  - HTTP non-2xx → [OpenInterestResult.Error] with "HTTP {code}".
 *  - Null body → [OpenInterestResult.Error].
 *  - ok=false or native=null → [OpenInterestResult.Success] with isEmpty==true.
 *  - Maps oiDelta, signal (via parseOiSignal), largeUp, largeDown, upperThresh, lowerThresh,
 *    sparkline (history oi_delta values oldest→newest, nulls dropped), signalAgeMs (from
 *    received_at parsed as ISO-8601; null if string is null or unparseable — still Success).
 *  - [CancellationException] is rethrown; never throws to callers.
 *
 * @param clock Injectable clock lambda for testability (defaults to System.currentTimeMillis).
 */
@Singleton
class OpenInterestRepositoryImpl @Inject constructor(
    private val openInterestApi: OpenInterestApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : OpenInterestRepository {

    override suspend fun fetchOpenInterest(): OpenInterestResult = withContext(ioDispatcher) {
        try {
            val response = openInterestApi.getOiNative()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext OpenInterestResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext OpenInterestResult.Error(message = "null body")
                }

            val native = body.native
            if (!body.ok || native == null) {
                return@withContext OpenInterestResult.Success(
                    OpenInterestData(
                        oiDelta = null,
                        signal = OiSignal.NONE,
                        largeUp = false,
                        largeDown = false,
                        upperThresh = null,
                        lowerThresh = null,
                        signalAgeMs = null,
                        sparkline = emptyList(),
                    )
                )
            }

            // Parse ISO-8601 received_at to epoch ms; null on missing or malformed string.
            val signalAgeMs: Long? = native.receivedAt?.let { iso ->
                try {
                    val receivedAtMs = Instant.parse(iso).toEpochMilli()
                    clock() - receivedAtMs
                } catch (e: Exception) {
                    null
                }
            }

            val sparkline = native.history.mapNotNull { it.oiDelta }

            OpenInterestResult.Success(
                OpenInterestData(
                    oiDelta = native.oiDelta,
                    signal = parseOiSignal(native.signal),
                    largeUp = native.largeOiUp,
                    largeDown = native.largeOiDw,
                    upperThresh = native.upperThresh,
                    lowerThresh = native.lowerThresh,
                    signalAgeMs = signalAgeMs,
                    sparkline = sparkline,
                )
            )
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            OpenInterestResult.Error(message = e.message)
        }
    }
}
