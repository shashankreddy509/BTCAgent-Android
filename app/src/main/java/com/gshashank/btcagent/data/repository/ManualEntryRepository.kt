package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ManualOrderDraft
import com.gshashank.btcagent.data.model.PendingOrder

/**
 * Repository interface for Manual Entry operations — MOBILE-19.
 *
 * All write methods return [ActionResult] (reused from PositionsResult.kt).
 * [fetchPending] returns a list of resting limit orders; never throws.
 * Implementations MUST NOT throw to callers.
 */
interface ManualEntryRepository {

    /** Places a market order. Returns [ActionResult.Success] on 2xx, [ActionResult.Error] otherwise. */
    suspend fun placeMarket(draft: ManualOrderDraft): ActionResult

    /** Places a limit order. Returns [ActionResult.Success] on 2xx, [ActionResult.Error] otherwise. */
    suspend fun placeLimit(draft: ManualOrderDraft): ActionResult

    /**
     * Cancels a pending limit order by its id.
     * @param pendingId the id of the pending order to cancel.
     */
    suspend fun cancelPending(pendingId: String): ActionResult

    /** Fetches the current list of resting limit orders. Returns empty list on error. */
    suspend fun fetchPending(): List<PendingOrder>
}
