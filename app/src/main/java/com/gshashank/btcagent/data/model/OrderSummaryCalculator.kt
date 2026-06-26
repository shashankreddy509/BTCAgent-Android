package com.gshashank.btcagent.data.model

/**
 * Domain value types and stateless calculator for the Manual Entry order form — MOBILE-19.
 *
 * Moved from ui.trade.manual to data.model — pure domain logic with no UI dependencies.
 */

data class OrderSummary(
    val notional: Double,
    val maxLoss: Double,
    val rr: Double?,
)

object OrderSummaryCalculator {

    /**
     * Computes an [OrderSummary] from the provided inputs.
     *
     * Returns null when any required field (qty, entry, sl) is null so the UI can hide the
     * summary card until all required fields are filled.
     *
     * Formulas:
     *   notional = qty * entry
     *   maxLoss  = qty * (entry - sl)   for LONG
     *   maxLoss  = qty * (sl - entry)   for SHORT
     *   rr       = (tp - entry) / (entry - sl)   for LONG  when tp != null
     *   rr       = (entry - tp) / (sl - entry)   for SHORT when tp != null
     *   rr       = null when tp is null
     */
    fun compute(
        qty: Double?,
        entry: Double?,
        sl: Double?,
        tp: Double?,
        direction: Side,
    ): OrderSummary? {
        if (qty == null || entry == null || sl == null) return null
        // entry == sl would make rr divide by zero (Infinity/NaN). An invalid SL is also caught
        // by the VM's slValidationError, but guard here so no consumer ever gets garbage.
        if (entry == sl) return null

        val notional = qty * entry
        val maxLoss = when (direction) {
            Side.Long -> qty * (entry - sl)
            Side.Short -> qty * (sl - entry)
        }
        val rr: Double? = if (tp != null) {
            when (direction) {
                Side.Long -> (tp - entry) / (entry - sl)
                Side.Short -> (entry - tp) / (sl - entry)
            }
        } else null

        return OrderSummary(notional = notional, maxLoss = maxLoss, rr = rr)
    }
}
