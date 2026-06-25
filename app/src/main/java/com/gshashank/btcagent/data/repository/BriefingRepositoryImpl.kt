package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.BriefingData
import com.gshashank.btcagent.data.network.BriefingApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [BriefingRepository] — MOBILE-9.
 *
 * Calls [BriefingApi.getBriefing] and maps the DTO to [BriefingData]:
 *  - Parses the ISO 8601 timestamp string to epoch milliseconds (Long).
 *  - A null timestamp maps to a null [BriefingData.timestampMs].
 *  - Returns [BriefingResult.Error] with "HTTP {code}" for non-2xx responses.
 *  - Returns [BriefingResult.Error] for null response body.
 *  - NEVER throws to callers; [CancellationException] is rethrown.
 */
@Singleton
class BriefingRepositoryImpl @Inject constructor(
    private val briefingApi: BriefingApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BriefingRepository {

    override suspend fun fetchBriefing(): BriefingResult = withContext(ioDispatcher) {
        try {
            val response = briefingApi.getBriefing()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext BriefingResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext BriefingResult.Error(message = "Empty response body")
                }

            val timestampMs: Long? = body.timestamp?.let { iso ->
                try {
                    Instant.parse(iso).toEpochMilli()
                } catch (e: Exception) {
                    null
                }
            }

            BriefingResult.Success(
                BriefingData(
                    timestampMs = timestampMs,
                    markdown = body.text,
                )
            )
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            BriefingResult.Error(message = e.message)
        }
    }
}
