package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.MarkovData
import com.gshashank.btcagent.data.model.StationaryDist
import com.gshashank.btcagent.data.model.TickerRegime
import com.gshashank.btcagent.data.model.parseRegime
import com.gshashank.btcagent.data.network.MarkovApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [MarkovRepository] — MOBILE-13.
 *
 * Calls [MarkovApi.getTickers] and maps the DTO to domain models:
 *  - HTTP non-2xx → [MarkovResult.Error] with "HTTP {code}".
 *  - Null body → [MarkovResult.Error] with "empty body".
 *  - Maps tickers using [parseRegime], [StationaryDist.fromList], hasError = dto.error != null.
 *  - Empty tickers list → [MarkovResult.Success] with [MarkovData.isEmpty] == true.
 *  - [CancellationException] is rethrown; never throws to callers.
 */
@Singleton
class MarkovRepositoryImpl @Inject constructor(
    private val markovApi: MarkovApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MarkovRepository {

    override suspend fun fetchTickers(): MarkovResult = withContext(ioDispatcher) {
        try {
            val response = markovApi.getTickers()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext MarkovResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext MarkovResult.Error(message = "empty body")
                }

            val tickers = body.tickers.map { dto ->
                TickerRegime(
                    ticker = dto.ticker,
                    market = dto.market,
                    regime = parseRegime(dto.regime),
                    conviction = dto.conviction,
                    stationary = StationaryDist.fromList(dto.stationary),
                    accuracy = dto.accuracy,
                    gradedCount = dto.gradedCount,
                    hasError = dto.error != null,
                )
            }

            MarkovResult.Success(MarkovData(tickers = tickers))
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            MarkovResult.Error(message = e.message)
        }
    }
}
