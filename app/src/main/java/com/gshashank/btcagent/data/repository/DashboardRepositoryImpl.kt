package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.BotMode
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.data.model.PriceDirection
import com.gshashank.btcagent.data.network.DashboardApi
import com.gshashank.btcagent.data.network.PriceWebSocketClient
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [DashboardRepository].
 *
 * [fetchState] calls [DashboardApi.getTradingState], maps the DTO to [DashboardData], and
 * NEVER throws to the caller — all exceptions are caught and returned as [DashboardResult.Error].
 *
 * Today's P&L is computed client-side by summing history[].pnl_closed for entries closed TODAY
 * in the device's local timezone. The server timestamp is a UTC instant ("2026-06-24T10:30:00Z");
 * it is parsed as an Instant and converted to the device zone before taking its LocalDate — so a
 * trade closed near midnight UTC lands in the correct LOCAL calendar day (a naive substringBefore
 * 'T' would use the UTC date and mis-bucket trades around the local day boundary).
 *
 * [priceFlow] delegates directly to [PriceWebSocketClient.priceFlow].
 */
@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val dashboardApi: DashboardApi,
    private val priceWebSocketClient: PriceWebSocketClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DashboardRepository {

    override suspend fun fetchState(): DashboardResult = withContext(ioDispatcher) {
        try {
            val response = dashboardApi.getTradingState()
            if (!response.isSuccessful) {
                return@withContext DashboardResult.Error(cause = null)
            }
            val body = response.body()
                ?: return@withContext DashboardResult.Error(cause = null)

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todayPnl = body.history
                .filter { it.closedAt != null && it.pnlClosed != null }
                .filter { entry ->
                    try {
                        // Parse the UTC instant and convert to the device zone before comparing
                        // calendar days, so trades near the local-day boundary bucket correctly.
                        Instant.parse(entry.closedAt!!).atZone(zone).toLocalDate() == today
                    } catch (e: Exception) {
                        false
                    }
                }
                .sumOf { it.pnlClosed!! }

            val openUnrealisedPnl = body.positions
                .mapNotNull { it.pnl }
                .sum()

            val botMode = if (body.settings.mode == "live") BotMode.Live else BotMode.Paper

            DashboardResult.Success(
                DashboardData(
                    // btcPrice = 0.0: the WS feed is the live price source; the REST snapshot
                    // current_price may be 0.0 when the scanner is stopped. The VM overlays the
                    // latest WS price on top of this REST result.
                    btcPrice = 0.0,
                    priceDirection = PriceDirection.Flat,
                    todayPnlPts = todayPnl,
                    openPositionCount = body.positions.size,
                    openUnrealisedPnl = openUnrealisedPnl,
                    botRunning = body.running,
                    botMode = botMode,
                )
            )
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            DashboardResult.Error(cause = e)
        }
    }

    override fun priceFlow(): Flow<Float> = priceWebSocketClient.priceFlow()
}
