package com.gshashank.btcagent.data.model

/**
 * OI signal direction, matching exact backend strings — MOBILE-11.
 */
enum class OiSignal { LONG, SHORT, NONE }

/**
 * Parses an OI signal string into [OiSignal].
 *
 * Exact case-sensitive matching: "LONG" → LONG, "SHORT" → SHORT, "NONE" → NONE.
 * Any other value (null, blank, unknown, wrong case) → NONE.
 */
fun parseOiSignal(s: String?): OiSignal = when (s) {
    "LONG" -> OiSignal.LONG
    "SHORT" -> OiSignal.SHORT
    "NONE" -> OiSignal.NONE
    else -> OiSignal.NONE
}

/**
 * Domain model for the Open Interest screen — MOBILE-11.
 *
 * @param oiDelta       Absolute signed OI delta in coin units (null when no snapshot).
 * @param signal        Parsed OI signal direction.
 * @param largeUp       True when a large up OI event is flagged.
 * @param largeDown     True when a large down OI event is flagged.
 * @param upperThresh   Upper threshold for signal detection.
 * @param lowerThresh   Lower threshold for signal detection.
 * @param signalAgeMs   Milliseconds since [receivedAt] (null if unparseable).
 * @param sparkline     5 OI-delta history points, oldest→newest; nulls already dropped.
 */
data class OpenInterestData(
    val oiDelta: Double?,
    val signal: OiSignal,
    val largeUp: Boolean,
    val largeDown: Boolean,
    val upperThresh: Double?,
    val lowerThresh: Double?,
    val signalAgeMs: Long?,
    val sparkline: List<Double>,
) {
    /** True when there is no meaningful data to display (no snapshot returned). */
    val isEmpty: Boolean get() = oiDelta == null && sparkline.isEmpty()
}
