package com.gshashank.btcagent.ui.trade.manual

import com.gshashank.btcagent.data.model.OrderSummary
import com.gshashank.btcagent.data.model.OrderSummaryCalculator
import com.gshashank.btcagent.data.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * JVM unit tests for the [OrderSummaryCalculator] pure function/object — MOBILE-19.
 *
 * The calculator is a stateless computation that derives [OrderSummary] from the inputs of the
 * order form. It is extracted from the ViewModel so it can be tested independently.
 *
 * Domain definitions:
 *   notional  = qty * entry
 *   maxLoss   = qty * (entry - sl)   for LONG   (sl < entry)
 *   maxLoss   = qty * (sl - entry)   for SHORT  (sl > entry)
 *   rr        = (tp - entry) / (entry - sl)   for LONG  when tp present
 *   rr        = (entry - tp) / (sl - entry)   for SHORT when tp present
 *   rr        = null when tp is null
 *
 * All tests MUST fail (red) until the production object/function is implemented at:
 *   `app/src/main/java/com/gshashank/btcagent/ui/trade/manual/OrderSummaryCalculator.kt`
 *
 * Test coverage:
 *   1.  notional = qty * entry
 *   2.  maxLoss LONG = qty * (entry - sl)
 *   3.  maxLoss SHORT = qty * (sl - entry) — NOT swapped with long formula
 *   4.  rr LONG with tp = (tp - entry) / (entry - sl)
 *   5.  rr = null when tp is null (LONG)
 *   6.  rr SHORT with tp = (entry - tp) / (sl - entry)
 *   7.  null-render guard: compute() returns null when qty is null
 *   8.  null-render guard: compute() returns null when entry is null
 *   9.  null-render guard: compute() returns null when sl is null
 *   10. All required fields present → non-null OrderSummary returned
 */
class OrderSummaryTest {

    private val delta = 0.0001 // floating-point tolerance

    // =========================================================================
    // 1. notional = qty * entry
    // =========================================================================

    @Test
    fun `notional equals qty times entry`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 0.5,
            entry = 60_000.0,
            sl = 59_000.0,
            tp = null,
        )

        assertNotNull("OrderSummary must be non-null when all required fields are provided", summary)
        assertEquals(
            "notional must equal qty * entry = 0.5 * 60000 = 30000",
            30_000.0,
            summary!!.notional,
            delta,
        )
    }

    // =========================================================================
    // 2. maxLoss LONG = qty * (entry - sl)
    // =========================================================================

    @Test
    fun `maxLoss for LONG equals qty times (entry minus sl)`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 1.0,
            entry = 50_000.0,
            sl = 49_000.0,
            tp = null,
        )

        assertNotNull("OrderSummary must be non-null for valid LONG inputs", summary)
        assertEquals(
            "maxLoss for LONG must be qty * (entry - sl) = 1.0 * (50000 - 49000) = 1000",
            1_000.0,
            summary!!.maxLoss,
            delta,
        )
    }

    // =========================================================================
    // 3. maxLoss SHORT = qty * (sl - entry) — discriminator: NOT swapped with LONG
    // =========================================================================

    @Test
    fun `maxLoss for SHORT equals qty times (sl minus entry) NOT swapped`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Short,
            qty = 1.0,
            entry = 50_000.0,
            sl = 51_500.0, // sl > entry for SHORT
            tp = null,
        )

        assertNotNull("OrderSummary must be non-null for valid SHORT inputs", summary)
        assertEquals(
            "DISCRIMINATOR: maxLoss for SHORT must be qty * (sl - entry) = 1.0 * (51500 - 50000) = 1500 — " +
                "must NOT use LONG formula qty * (entry - sl) which would give a negative maxLoss",
            1_500.0,
            summary!!.maxLoss,
            delta,
        )
    }

    @Test
    fun `maxLoss SHORT is always positive (not same sign as LONG formula)`() {
        val longSummary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 2.0,
            entry = 50_000.0,
            sl = 48_000.0,
            tp = null,
        )
        val shortSummary = OrderSummaryCalculator.compute(
            direction = Side.Short,
            qty = 2.0,
            entry = 50_000.0,
            sl = 52_000.0,
            tp = null,
        )

        assertNotNull(longSummary)
        assertNotNull(shortSummary)

        // Both must be positive values (qty * |entry - sl|)
        assertEquals(
            "LONG maxLoss must equal 2.0 * (50000 - 48000) = 4000",
            4_000.0,
            longSummary!!.maxLoss,
            delta,
        )
        assertEquals(
            "SHORT maxLoss must equal 2.0 * (52000 - 50000) = 4000 — symmetric with LONG example",
            4_000.0,
            shortSummary!!.maxLoss,
            delta,
        )
    }

    // =========================================================================
    // 4. rr LONG with tp = (tp - entry) / (entry - sl)
    // =========================================================================

    @Test
    fun `rr for LONG with tp present equals (tp minus entry) divided by (entry minus sl)`() {
        // entry=50000, sl=49000, tp=53000
        // rr = (53000 - 50000) / (50000 - 49000) = 3000 / 1000 = 3.0
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 1.0,
            entry = 50_000.0,
            sl = 49_000.0,
            tp = 53_000.0,
        )

        assertNotNull("OrderSummary must be non-null when all required fields including tp are provided", summary)
        assertNotNull("rr must be non-null when tp is provided", summary!!.rr)
        assertEquals(
            "rr for LONG must be (tp - entry) / (entry - sl) = (53000 - 50000) / (50000 - 49000) = 3.0",
            3.0,
            summary.rr!!,
            delta,
        )
    }

    // =========================================================================
    // 5. rr = null when tp is null (LONG)
    // =========================================================================

    @Test
    fun `rr is null when tp is not provided for LONG`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 1.0,
            entry = 50_000.0,
            sl = 49_000.0,
            tp = null,
        )

        assertNotNull("OrderSummary must still be non-null when tp is absent", summary)
        assertNull(
            "rr must be null when tp is null — no TP means no R:R ratio to display",
            summary!!.rr,
        )
    }

    // =========================================================================
    // 6. rr SHORT with tp = (entry - tp) / (sl - entry)
    // =========================================================================

    @Test
    fun `rr for SHORT with tp present equals (entry minus tp) divided by (sl minus entry)`() {
        // entry=50000, sl=52000, tp=44000
        // rr = (50000 - 44000) / (52000 - 50000) = 6000 / 2000 = 3.0
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Short,
            qty = 1.0,
            entry = 50_000.0,
            sl = 52_000.0,
            tp = 44_000.0,
        )

        assertNotNull("OrderSummary must be non-null when all SHORT fields including tp provided", summary)
        assertNotNull("rr must be non-null when tp is provided for SHORT", summary!!.rr)
        assertEquals(
            "rr for SHORT must be (entry - tp) / (sl - entry) = (50000 - 44000) / (52000 - 50000) = 3.0",
            3.0,
            summary.rr!!,
            delta,
        )
    }

    // =========================================================================
    // 7. null-render guard: compute() returns null when qty is null
    // =========================================================================

    @Test
    fun `compute returns null when qty is null`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = null,
            entry = 50_000.0,
            sl = 49_000.0,
            tp = null,
        )

        assertNull(
            "OrderSummary must be null when qty is null — cannot compute without all required fields",
            summary,
        )
    }

    // =========================================================================
    // 8. null-render guard: compute() returns null when entry is null
    // =========================================================================

    @Test
    fun `compute returns null when entry is null`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 0.01,
            entry = null,
            sl = 49_000.0,
            tp = null,
        )

        assertNull(
            "OrderSummary must be null when entry is null — cannot compute notional without entry",
            summary,
        )
    }

    // =========================================================================
    // 9. null-render guard: compute() returns null when sl is null
    // =========================================================================

    @Test
    fun `compute returns null when sl is null`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 0.01,
            entry = 50_000.0,
            sl = null,
            tp = null,
        )

        assertNull(
            "OrderSummary must be null when sl is null — cannot compute maxLoss or rr without sl",
            summary,
        )
    }

    // =========================================================================
    // 10. All required fields present → non-null OrderSummary returned
    // =========================================================================

    @Test
    fun `compute returns non-null OrderSummary when all required fields are present`() {
        val summary = OrderSummaryCalculator.compute(
            direction = Side.Long,
            qty = 0.01,
            entry = 50_000.0,
            sl = 49_000.0,
            tp = null, // tp is optional
        )

        assertNotNull(
            "OrderSummary must be non-null when qty, entry, and sl are all non-null",
            summary,
        )
        assertTrue(
            "notional must be positive, got ${summary?.notional}",
            (summary?.notional ?: 0.0) > 0.0,
        )
        assertTrue(
            "maxLoss must be positive, got ${summary?.maxLoss}",
            (summary?.maxLoss ?: 0.0) > 0.0,
        )
    }
}
