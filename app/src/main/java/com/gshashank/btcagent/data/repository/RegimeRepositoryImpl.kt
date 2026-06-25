package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.LiveRegime
import com.gshashank.btcagent.data.model.RegimeData
import com.gshashank.btcagent.data.model.RegimeDay
import com.gshashank.btcagent.data.model.parseRegime
import com.gshashank.btcagent.data.network.RegimeApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [RegimeRepository] — MOBILE-12.
 *
 * Calls [RegimeApi.getRegimeLog] and maps the DTO to domain models:
 *  - Backend sends rows newest-first; takes the most-recent 14 (take, not takeLast) and sorts
 *    them ascending by date so the day-strip renders oldest→newest (left→right) chronologically.
 *  - Parses regime strings via [parseRegime]; unknown strings map to [Regime.UNKNOWN].
 *  - liveRegime DTO maps to [LiveRegime]; hasError = error field != null.
 *  - Returns [RegimeResult.Error] with "HTTP {code}" for non-2xx responses.
 *  - Returns [RegimeResult.Error] for null response body.
 *  - NEVER throws to callers; [CancellationException] is rethrown.
 */
@Singleton
class RegimeRepositoryImpl @Inject constructor(
    private val regimeApi: RegimeApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RegimeRepository {

    override suspend fun fetchRegime(): RegimeResult = withContext(ioDispatcher) {
        try {
            val response = regimeApi.getRegimeLog()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext RegimeResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext RegimeResult.Error(message = "null body")
                }

            // Backend sends rows newest-first → take(14) keeps the most-recent fortnight, then
            // sort ascending by date for chronological (oldest→newest, left→right) display.
            val days = body.rows
                .take(14)
                .sortedBy { it.date }
                .map { dto ->
                    RegimeDay(
                        date = dto.date,
                        regime = parseRegime(dto.predictedRegime),
                        correct = dto.correct,
                    )
                }

            val live = body.liveRegime?.let { dto ->
                LiveRegime(
                    regime = parseRegime(dto.regime),
                    conviction = dto.conviction,
                    hasError = dto.error != null,
                )
            }

            RegimeResult.Success(
                RegimeData(
                    live = live,
                    accuracyPct = body.accuracy,
                    gradedCount = body.gradedCount,
                    days = days,
                )
            )
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            RegimeResult.Error(message = e.message)
        }
    }
}
