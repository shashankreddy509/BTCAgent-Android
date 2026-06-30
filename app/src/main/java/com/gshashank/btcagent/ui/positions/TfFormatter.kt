package com.gshashank.btcagent.ui.positions

/**
 * Converts a timeframe expressed in minutes (as sent by the backend as a bare Int) into
 * a human-readable label — MOBILE-43.
 *
 * Rules:
 *   - Exact multiples of 60 → "<N>H"  (e.g. 60 → "1H", 240 → "4H")
 *   - Non-multiples of 60  → "<N>m"  (e.g. 30 → "30m", 15 → "15m")
 *   - null input            → ""      (empty string; no NPE)
 */
fun formatTfMinutes(tf: Int?): String {
    if (tf == null) return ""
    return if (tf % 60 == 0) {
        "${tf / 60}H"
    } else {
        "${tf}m"
    }
}
