package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.model.TradingControlData
import com.gshashank.btcagent.data.network.PositionDto
import com.gshashank.btcagent.data.network.SettingsWriteRequest
import com.gshashank.btcagent.data.network.TradingControlApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [TradingControlRepository] — MOBILE-18.
 *
 * All methods run on [ioDispatcher], never throw to callers, and rethrow [CancellationException]
 * so coroutine cancellation propagates correctly.
 *
 * [fetchState] calls [TradingControlApi.getTradingState] and maps the DTO to [TradingControlData].
 * Mode mapping: "live" → [ExecutionMode.LIVE], anything else → [ExecutionMode.PAPER].
 * Positions are mapped using the same P&L calculation as [PositionsRepositoryImpl].
 *
 * [setMode] sends only {mode:"live"} or {mode:"paper"} — depoEntryFilter is omitted (null).
 * [setDepoAlerts] sends only {depo_entry_filter:true/false} — mode is omitted (null).
 */
@Singleton
class TradingControlRepositoryImpl @Inject constructor(
    private val tradingControlApi: TradingControlApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TradingControlRepository {

    override suspend fun fetchState(): TradingControlResult = withContext(ioDispatcher) {
        try {
            val response = tradingControlApi.getTradingState()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext TradingControlResult.Error(
                    message = "HTTP ${response.code()}",
                )
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext TradingControlResult.Error(message = "Empty response body")
                }

            val currentPrice = body.currentPrice
            val mode = if (body.settings.mode == "live") ExecutionMode.LIVE else ExecutionMode.PAPER
            val depoAlertsEnabled = body.settings.depoEntryFilter
            val positions = body.positions.mapNotNull { dto ->
                dto.toPosition(currentPrice)
            }

            TradingControlResult.Success(
                TradingControlData(
                    running = body.running,
                    mode = mode,
                    depoAlertsEnabled = depoAlertsEnabled,
                    positions = positions,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TradingControlResult.Error(message = "Failed to load trading state")
        }
    }

    override suspend fun start(): ActionResult = withContext(ioDispatcher) {
        try {
            val response = tradingControlApi.start()
            if (response.isSuccessful) {
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                ActionResult.Error(
                    code = response.code(),
                    message = "Server error (${response.code()})",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Failed to start scanner")
        }
    }

    override suspend fun stop(): ActionResult = withContext(ioDispatcher) {
        try {
            val response = tradingControlApi.stop()
            if (response.isSuccessful) {
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                ActionResult.Error(
                    code = response.code(),
                    message = "Server error (${response.code()})",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Failed to stop scanner")
        }
    }

    override suspend fun setMode(mode: String): ActionResult = withContext(ioDispatcher) {
        try {
            val response = tradingControlApi.setSettings(SettingsWriteRequest(mode = mode))
            if (response.isSuccessful) {
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                ActionResult.Error(
                    code = response.code(),
                    message = "Server error (${response.code()})",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Failed to set mode")
        }
    }

    override suspend fun setDepoAlerts(enabled: Boolean): ActionResult = withContext(ioDispatcher) {
        try {
            val response = tradingControlApi.setSettings(
                SettingsWriteRequest(depoEntryFilter = enabled)
            )
            if (response.isSuccessful) {
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                ActionResult.Error(
                    code = response.code(),
                    message = "Server error (${response.code()})",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Failed to set DEPO alerts")
        }
    }

    override suspend fun close(signalId: String): ActionResult = withContext(ioDispatcher) {
        try {
            val response = tradingControlApi.cancel(signalId)
            if (response.isSuccessful) {
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                ActionResult.Error(
                    code = response.code(),
                    message = "Server error (${response.code()})",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = "Failed to close position")
        }
    }

    private fun PositionDto.toPosition(currentPrice: Double): Position? {
        val signalId = signalId ?: return null
        val entryPrice = entryPrice ?: return null
        val qty = qty ?: return null
        val direction = direction ?: return null

        val side = when (direction.lowercase()) {
            "long" -> Side.Long
            "short" -> Side.Short
            else -> return null
        }

        // effectiveSize: qty * contractSize if contractSize is present and > 0, else qty alone
        val effectiveSize = if (contractSize != null && contractSize > 0.0) {
            qty * contractSize
        } else {
            qty
        }

        val pnl = when (side) {
            Side.Long -> (currentPrice - entryPrice) * effectiveSize
            Side.Short -> (entryPrice - currentPrice) * effectiveSize
        }

        val notional = entryPrice * effectiveSize
        val pnlPct = if (notional != 0.0) (pnl / notional) * 100.0 else 0.0

        return Position(
            signalId = signalId,
            side = side,
            entryPrice = entryPrice,
            currentPrice = currentPrice,
            qty = qty,
            sl = sl,
            tp = tp,
            status = status ?: "unknown",
            openedAt = openedAt ?: "",
            pnl = pnl,
            pnlPct = pnlPct,
            contractSize = contractSize ?: 0.0,
        )
    }
}
