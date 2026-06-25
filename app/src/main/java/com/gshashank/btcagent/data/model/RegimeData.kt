package com.gshashank.btcagent.data.model

/**
 * Markov regime classification. [displayName] is the exact backend/spec casing
 * ("Bull"/"Bear"/"Sideways") for on-screen labels — never use [Enum.name] in the UI.
 */
enum class Regime(val displayName: String) {
    BULL("Bull"),
    BEAR("Bear"),
    SIDEWAYS("Sideways"),
    UNKNOWN("Unknown"),
}

fun parseRegime(s: String?): Regime = when (s) {
    "Bull" -> Regime.BULL
    "Bear" -> Regime.BEAR
    "Sideways" -> Regime.SIDEWAYS
    else -> Regime.UNKNOWN
}

data class LiveRegime(
    val regime: Regime,
    val conviction: Double?,
    val hasError: Boolean,
)

data class RegimeDay(
    val date: String,
    val regime: Regime,
    val correct: Boolean?,
)

data class RegimeData(
    val live: LiveRegime?,
    /** Model accuracy as a FRACTION 0.0–1.0 (backend `accuracy`); UI multiplies by 100. Null if ungraded. */
    val accuracyPct: Double?,
    val gradedCount: Int,
    val days: List<RegimeDay>,
) {
    val isEmpty: Boolean get() = live == null && days.isEmpty()
}
