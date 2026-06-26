package com.gshashank.btcagent.data.repository

/**
 * Repository interface for the Trading Control screen — MOBILE-18.
 *
 * All write methods return [ActionResult] (reused from PositionsResult.kt).
 * [fetchState] returns [TradingControlResult].
 * Implementations MUST NOT throw to callers.
 *
 * [setMode] accepts a String ("live" or "paper") so the interface stays free of domain enums
 * that the ViewModel converts before calling.
 */
interface TradingControlRepository {

    /** Fetches the full trading state (running, mode, depoAlerts, positions). */
    suspend fun fetchState(): TradingControlResult

    /** Starts the scanner. */
    suspend fun start(): ActionResult

    /** Stops the scanner. */
    suspend fun stop(): ActionResult

    /**
     * Sets the execution mode.
     * @param mode "live" or "paper"
     */
    suspend fun setMode(mode: String): ActionResult

    /**
     * Enables or disables the DEPO entry filter (depo_entry_filter).
     */
    suspend fun setDepoAlerts(enabled: Boolean): ActionResult

    /**
     * Closes an open position by its signal ID.
     */
    suspend fun close(signalId: String): ActionResult
}
