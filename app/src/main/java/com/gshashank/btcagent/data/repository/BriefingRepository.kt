package com.gshashank.btcagent.data.repository

/**
 * Data contract for the Morning Briefing feature — MOBILE-9.
 */
interface BriefingRepository {
    suspend fun fetchBriefing(): BriefingResult
}
