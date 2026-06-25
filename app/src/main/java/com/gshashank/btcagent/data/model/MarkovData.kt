package com.gshashank.btcagent.data.model

/**
 * Domain model for the Markov Matrix feature — MOBILE-13.
 *
 * [Regime] and [parseRegime] are imported from [RegimeData.kt] — NOT redefined here.
 */
data class MarkovData(val tickers: List<TickerRegime>) {
    val isEmpty: Boolean get() = tickers.isEmpty()
}

data class TickerRegime(
    val ticker: String,
    val market: String,
    val regime: Regime,
    val conviction: Double?,
    val stationary: StationaryDist?,
    val accuracy: Double?,
    val gradedCount: Int,
    val hasError: Boolean,
)

/**
 * Long-run (stationary) probability distribution over the three Markov states.
 *
 * Indices: [0]=Bear, [1]=Sideways, [2]=Bull — matching the backend `stationary` array.
 */
data class StationaryDist(val bear: Double, val sideways: Double, val bull: Double) {
    companion object {
        /**
         * Parses a [StationaryDist] from a list of exactly 3 doubles.
         * Returns null for any other list size — no crash.
         */
        fun fromList(l: List<Double>): StationaryDist? =
            if (l.size == 3) StationaryDist(l[0], l[1], l[2]) else null
    }
}
