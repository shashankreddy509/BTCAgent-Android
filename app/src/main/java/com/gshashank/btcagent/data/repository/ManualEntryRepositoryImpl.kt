package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ManualOrderDraft
import com.gshashank.btcagent.data.model.PendingOrder
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.network.ManualEntryApi
import com.gshashank.btcagent.data.network.ManualLimitRequestDto
import com.gshashank.btcagent.data.network.ManualMarketRequestDto
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [ManualEntryRepository] — MOBILE-19.
 *
 * Rules:
 * - NEVER throws to callers; CancellationException is rethrown.
 * - Closes errorBody() on non-2xx to prevent OkHttp connection pool exhaustion.
 * - 403 → admin-only user-friendly message.
 * - Error messages are masked (HTTP status code only, never raw server text).
 * - direction is sent lowercase ("long" / "short").
 * - [fetchPending] reads manual_pending[] from [ManualEntryApi.getTradingState]; returns empty
 *   list on any error.
 *
 * W4 note: ManualEntryApi.getTradingState() shares the /api/trading/state endpoint shape with
 * TradingControlApi and PositionsApi intentionally — separate injection avoids cross-layer
 * coupling in the repository. This is acceptable per code review guidance.
 */
@Singleton
class ManualEntryRepositoryImpl @Inject constructor(
    private val manualEntryApi: ManualEntryApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ManualEntryRepository {

    override suspend fun placeMarket(draft: ManualOrderDraft): ActionResult =
        withContext(ioDispatcher) {
            try {
                val response = manualEntryApi.placeMarket(
                    ManualMarketRequestDto(
                        direction = draft.direction.toApiString(),
                        qty = draft.qty,
                        sl = draft.sl,
                        tp = draft.tp,
                    )
                )
                if (response.isSuccessful) {
                    ActionResult.Success
                } else {
                    response.errorBody()?.close()
                    if (response.code() == 403) {
                        ActionResult.Error(
                            code = 403,
                            message = "Manual trading isn't enabled for your account yet",
                        )
                    } else {
                        ActionResult.Error(
                            code = response.code(),
                            message = "Server error (${response.code()})",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ActionResult.Error(code = -1, message = "Server error")
            }
        }

    override suspend fun placeLimit(draft: ManualOrderDraft): ActionResult =
        withContext(ioDispatcher) {
            val limitPrice = draft.limitPrice
                ?: return@withContext ActionResult.Error(code = -1, message = "limit_price required")
            try {
                val response = manualEntryApi.placeLimit(
                    ManualLimitRequestDto(
                        direction = draft.direction.toApiString(),
                        qty = draft.qty,
                        limitPrice = limitPrice,
                        sl = draft.sl,
                        tp = draft.tp,
                    )
                )
                if (response.isSuccessful) {
                    ActionResult.Success
                } else {
                    response.errorBody()?.close()
                    if (response.code() == 403) {
                        ActionResult.Error(
                            code = 403,
                            message = "Manual trading isn't enabled for your account yet",
                        )
                    } else {
                        ActionResult.Error(
                            code = response.code(),
                            message = "Server error (${response.code()})",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ActionResult.Error(code = -1, message = "Server error")
            }
        }

    override suspend fun cancelPending(pendingId: String): ActionResult =
        withContext(ioDispatcher) {
            try {
                val response = manualEntryApi.cancelPending(pendingId)
                if (response.isSuccessful) {
                    ActionResult.Success
                } else {
                    response.errorBody()?.close()
                    if (response.code() == 403) {
                        ActionResult.Error(
                            code = 403,
                            message = "Manual trading isn't enabled for your account yet",
                        )
                    } else {
                        ActionResult.Error(
                            code = response.code(),
                            message = "Server error (${response.code()})",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ActionResult.Error(code = -1, message = "Server error")
            }
        }

    override suspend fun fetchPending(): List<PendingOrder> = withContext(ioDispatcher) {
        try {
            val response = manualEntryApi.getTradingState()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext emptyList()
            }
            val body = response.body() ?: return@withContext emptyList()
            body.manualPending.mapNotNull { dto ->
                val direction = when (dto.direction.lowercase()) {
                    "long" -> Side.Long
                    "short" -> Side.Short
                    else -> return@mapNotNull null
                }
                PendingOrder(
                    id = dto.id,
                    direction = direction,
                    qty = dto.qty,
                    limitPrice = dto.limitPrice,
                    sl = dto.sl,
                    tp = dto.tp,
                    createdAt = dto.createdAt,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Side.toApiString() = when (this) {
        Side.Long -> "long"
        Side.Short -> "short"
    }
}
