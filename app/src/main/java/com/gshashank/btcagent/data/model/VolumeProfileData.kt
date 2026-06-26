package com.gshashank.btcagent.data.model

/**
 * Timeframe enum for Volume Profile sessions — MOBILE-14.
 * Keys match the backend JSON keys exactly.
 */
enum class Timeframe(val key: String) {
    H4("4h"),
    H12("12h"),
    D1("1d"),
}

/**
 * Domain model for a single volume-profile session — MOBILE-14.
 *
 * [hasData] is true only when ALL profile fields are non-null. The backend writes them
 * all-or-nothing, but this guards the UI from rendering "null" if any single field is absent.
 */
data class Session(
    val start: String,
    val poc: Double?,
    val vah: Double?,
    val vaLow: Double?,
    val lo: Double?,
    val hi: Double?,
) {
    val hasData get() = poc != null && vah != null && vaLow != null && lo != null && hi != null
}

/**
 * Domain model for the full volume-profile response — MOBILE-14.
 *
 * [isEmpty] is true when all timeframe session lists are empty.
 */
data class VolumeProfileData(
    val timeframes: Map<Timeframe, List<Session>>,
    val version: Int,
) {
    val isEmpty get() = timeframes.values.all { it.isEmpty() }
}
