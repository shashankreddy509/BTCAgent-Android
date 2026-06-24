package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.network.EditTpSlRequest
import com.gshashank.btcagent.data.network.PositionDto
import com.gshashank.btcagent.data.network.PositionsApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [PositionsRepository] — MOBILE-6.
 *
 * [fetchPositions] calls [PositionsApi.getTradingState], reads positions[] and current_price
 * from the response root, and computes live P&L for each position using the formula:
 *   - long:  (currentPrice − entryPrice) * effectiveSize
 *   - short: (entryPrice − currentPrice) * effectiveSize
 *   - effectiveSize = qty * contractSize  (fallback to qty when contractSize is null or 0)
 *   - pnlPct = (pnl / notional) * 100  where notional = entryPrice * effectiveSize
 *
 * NEVER throws to callers — all exceptions are caught and mapped to error results.
 * [CancellationException] is rethrown so coroutine cancellation propagates correctly.
 */
@Singleton
class PositionsRepositoryImpl @Inject constructor(
    private val positionsApi: PositionsApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PositionsRepository {

    override suspend fun fetchPositions(): PositionsResult = withContext(ioDispatcher) {
        try {
            val response = positionsApi.getTradingState()
            if (!response.isSuccessful) {
                response.errorBody()?.close() // release the connection back to the pool
                return@withContext PositionsResult.Error(
                    message = "HTTP ${response.code()}",
                )
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext PositionsResult.Error(message = "Empty response body")
                }

            val currentPrice = body.currentPrice
            val positions = body.positions.mapNotNull { dto ->
                dto.toPosition(currentPrice)
            }
            PositionsResult.Success(positions)
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            PositionsResult.Error(message = e.message)
        }
    }

    override suspend fun close(signalId: String): ActionResult = withContext(ioDispatcher) {
        try {
            val response = positionsApi.cancel(signalId)
            if (response.isSuccessful) {
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                ActionResult.Error(
                    code = response.code(),
                    message = response.message() ?: "Unknown error",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = e.message ?: "Unknown error")
        }
    }

    override suspend fun editTpSl(
        signalId: String,
        sl: Double?,
        tp: Double?,
    ): ActionResult = withContext(ioDispatcher) {
        try {
            val response = positionsApi.edit(signalId, EditTpSlRequest(sl = sl, tp = tp))
            if (response.isSuccessful) {
                ActionResult.Success
            } else {
                response.errorBody()?.close()
                ActionResult.Error(
                    code = response.code(),
                    message = response.message() ?: "Unknown error",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Error(code = -1, message = e.message ?: "Unknown error")
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
