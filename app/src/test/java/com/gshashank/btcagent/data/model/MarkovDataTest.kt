package com.gshashank.btcagent.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for [StationaryDist.fromList] — MOBILE-13.
 *
 * [StationaryDist] is a value type holding long-run [Bear, Sideways, Bull] probabilities
 * parsed from the backend `stationary` array. The companion [StationaryDist.fromList]
 * returns a valid [StationaryDist] only when the input list has exactly 3 elements;
 * otherwise it returns null (no crash).
 *
 * All tests MUST fail (red) until [MarkovData], [TickerRegime], and [StationaryDist] are
 * implemented in `data/model/MarkovData.kt`.
 *
 * Test coverage:
 *   1. [0.2, 0.3, 0.5] → StationaryDist(bear=0.2, sideways=0.3, bull=0.5)
 *   2. Empty list → null
 *   3. List of 2 elements → null
 *   4. List of 4 elements → null
 */
class MarkovDataTest {

    // =========================================================================
    // 1. [0.2, 0.3, 0.5] → StationaryDist(bear=0.2, sideways=0.3, bull=0.5)
    // =========================================================================

    @Test
    fun `fromList with 3 elements returns StationaryDist with correct bear sideways bull values`() {
        val input = listOf(0.2, 0.3, 0.5)

        val result = StationaryDist.fromList(input)

        assertNotNull(
            "fromList([0.2, 0.3, 0.5]) must return a non-null StationaryDist",
            result,
        )
        assertEquals(
            "bear (index 0) must be 0.2",
            0.2,
            result!!.bear,
            0.0001,
        )
        assertEquals(
            "sideways (index 1) must be 0.3",
            0.3,
            result.sideways,
            0.0001,
        )
        assertEquals(
            "bull (index 2) must be 0.5",
            0.5,
            result.bull,
            0.0001,
        )
    }

    // =========================================================================
    // 2. Empty list → null
    // =========================================================================

    @Test
    fun `fromList with empty list returns null`() {
        val result = StationaryDist.fromList(emptyList())

        assertNull(
            "fromList(emptyList()) must return null — no valid distribution possible",
            result,
        )
    }

    // =========================================================================
    // 3. List of 2 elements → null
    // =========================================================================

    @Test
    fun `fromList with 2 elements returns null`() {
        val result = StationaryDist.fromList(listOf(0.5, 0.5))

        assertNull(
            "fromList([0.5, 0.5]) must return null — a 2-element list cannot form a 3-state distribution",
            result,
        )
    }

    // =========================================================================
    // 4. List of 4 elements → null
    // =========================================================================

    @Test
    fun `fromList with 4 elements returns null`() {
        val result = StationaryDist.fromList(listOf(0.1, 0.2, 0.3, 0.4))

        assertNull(
            "fromList([0.1, 0.2, 0.3, 0.4]) must return null — only exactly 3 elements are valid",
            result,
        )
    }

    // =========================================================================
    // Additional: all-zero probabilities with 3 elements → non-null (boundary)
    // =========================================================================

    @Test
    fun `fromList with 3 zero elements returns non-null StationaryDist`() {
        val result = StationaryDist.fromList(listOf(0.0, 0.0, 0.0))

        assertNotNull(
            "fromList([0.0, 0.0, 0.0]) must return a non-null StationaryDist — " +
                "zero values are valid; only list size matters",
            result,
        )
    }

    // =========================================================================
    // Additional: MarkovData isEmpty true when tickers list is empty
    // =========================================================================

    @Test
    fun `MarkovData isEmpty is true when tickers list is empty`() {
        val data = MarkovData(tickers = emptyList())

        assertTrue(
            "MarkovData.isEmpty must return true when tickers list is empty",
            data.isEmpty,
        )
    }

    // =========================================================================
    // Additional: MarkovData isEmpty false when tickers list is non-empty
    // =========================================================================

    @Test
    fun `MarkovData isEmpty is false when tickers list is non-empty`() {
        val ticker = TickerRegime(
            ticker = "BTC-USD",
            market = "crypto",
            regime = Regime.BULL,
            conviction = 0.9,
            stationary = StationaryDist(bear = 0.1, sideways = 0.2, bull = 0.7),
            accuracy = 0.8,
            gradedCount = 20,
            hasError = false,
        )
        val data = MarkovData(tickers = listOf(ticker))

        assertFalse(
            "MarkovData.isEmpty must return false when tickers list is non-empty",
            data.isEmpty,
        )
    }
}

// Bring assertTrue/assertFalse into scope — they are on JUnit Assert but using extension-style here.
private fun assertTrue(message: String, condition: Boolean) =
    org.junit.Assert.assertTrue(message, condition)

private fun assertFalse(message: String, condition: Boolean) =
    org.junit.Assert.assertFalse(message, condition)
