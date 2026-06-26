package com.gshashank.btcagent.ui.trade.manual

/**
 * Package-level assertion helpers for tests in this package — MOBILE-19.
 *
 * [OrderSummaryTest] uses [assertTrue] without an explicit import. Declaring it here makes it
 * available to all tests in this package without modifying existing test files.
 */
fun assertTrue(message: String, actual: Boolean) {
    org.junit.Assert.assertTrue(message, actual)
}
