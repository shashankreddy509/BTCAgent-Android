package com.gshashank.btcagent.ui.components.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [UiState] sealed interface and its helper extension functions —
 * MOBILE-24 (Data-State Scaffolding).
 *
 * No Compose runtime, no ViewModel, no Android framework — pure Kotlin.
 *
 * All tests are expected to FAIL (red) until [UiState.kt] is implemented. The
 * implementation does not exist yet; these tests will not compile until the sealed
 * interface and extensions are written.
 *
 * Catalog flag: NOT needed for this story (MOBILE-24 has no catalog gate).
 */
class UiStateTest {

    // =========================================================================
    // 1. isTerminal — Loading, Empty, Ready → false; Error, Offline → true
    // =========================================================================

    @Test
    fun `isTerminal returns false for Loading`() {
        val state: UiState<String> = UiState.Loading

        assertFalse(
            "UiState.Loading must not be terminal — it is a transient in-progress state",
            state.isTerminal(),
        )
    }

    @Test
    fun `isTerminal returns false for Empty`() {
        val state: UiState<String> = UiState.Empty

        assertFalse(
            "UiState.Empty must not be terminal — the caller can still request a refresh",
            state.isTerminal(),
        )
    }

    @Test
    fun `isTerminal returns false for Ready`() {
        val state: UiState<String> = UiState.Ready("data")

        assertFalse(
            "UiState.Ready must not be terminal — it is the success/live-data state",
            state.isTerminal(),
        )
    }

    @Test
    fun `isTerminal returns true for Error`() {
        val state: UiState<String> = UiState.Error(code = "ERR_404", message = "Not found")

        assertTrue(
            "UiState.Error must be terminal — callers may stop polling on error",
            state.isTerminal(),
        )
    }

    @Test
    fun `isTerminal returns true for Offline`() {
        val state: UiState<String> = UiState.Offline(lastUpdatedMs = 0L, hasCache = false)

        assertTrue(
            "UiState.Offline must be terminal — callers may stop polling when offline",
            state.isTerminal(),
        )
    }

    // =========================================================================
    // 2. map — Ready transforms payload
    // =========================================================================

    @Test
    fun `map on Ready transforms the payload`() {
        val state: UiState<Int> = UiState.Ready(42)

        val result = state.map { it * 2 }

        assertEquals(
            "map on UiState.Ready must apply the transform to the inner data",
            UiState.Ready(84),
            result,
        )
    }

    // =========================================================================
    // 3. map — non-Ready variants pass through typed (no transform executed)
    // =========================================================================

    @Test
    fun `map on Loading passes through as Loading`() {
        val state: UiState<Int> = UiState.Loading

        val result = state.map { it * 2 }

        assertEquals(
            "map on UiState.Loading must return UiState.Loading without executing the transform",
            UiState.Loading,
            result,
        )
    }

    @Test
    fun `map on Empty passes through as Empty`() {
        val state: UiState<Int> = UiState.Empty

        val result = state.map { it * 2 }

        assertEquals(
            "map on UiState.Empty must return UiState.Empty without executing the transform",
            UiState.Empty,
            result,
        )
    }

    @Test
    fun `map on Error passes through as same Error`() {
        val original: UiState<Int> = UiState.Error(code = "E", message = "msg")

        val result = original.map { it * 2 }

        assertEquals(
            "map on UiState.Error must return the same Error instance without executing the transform",
            original,
            result,
        )
    }

    @Test
    fun `map on Offline passes through as same Offline`() {
        val original: UiState<Int> = UiState.Offline(lastUpdatedMs = 0L, hasCache = false)

        val result = original.map { it * 2 }

        assertEquals(
            "map on UiState.Offline must return the same Offline instance without executing the transform",
            original,
            result,
        )
    }

    // =========================================================================
    // 4. dataOrNull — Ready returns data
    // =========================================================================

    @Test
    fun `dataOrNull on Ready returns the wrapped data`() {
        val state: UiState<String> = UiState.Ready("hello")

        val result = state.dataOrNull()

        assertEquals(
            "dataOrNull must return the inner data when state is Ready",
            "hello",
            result,
        )
    }

    // =========================================================================
    // 5. dataOrNull — non-Ready variants return null
    // =========================================================================

    @Test
    fun `dataOrNull on Loading returns null`() {
        val state: UiState<String> = UiState.Loading

        assertNull(
            "dataOrNull must return null when state is Loading",
            state.dataOrNull(),
        )
    }

    @Test
    fun `dataOrNull on Empty returns null`() {
        val state: UiState<String> = UiState.Empty

        assertNull(
            "dataOrNull must return null when state is Empty",
            state.dataOrNull(),
        )
    }

    @Test
    fun `dataOrNull on Error returns null`() {
        val state: UiState<String> = UiState.Error(code = "ERR_500", message = "Server error")

        assertNull(
            "dataOrNull must return null when state is Error",
            state.dataOrNull(),
        )
    }

    @Test
    fun `dataOrNull on Offline returns null`() {
        val state: UiState<String> = UiState.Offline(lastUpdatedMs = 1000L, hasCache = true)

        assertNull(
            "dataOrNull must return null when state is Offline",
            state.dataOrNull(),
        )
    }

    // =========================================================================
    // 6. Exhaustive when — compile-time coverage across all five branches
    //
    // NOTE: This test verifies that a `when(uiState)` without `else` is exhaustive.
    // The Kotlin compiler enforces exhaustiveness on sealed interfaces in `when`
    // expressions, so this test has no runtime assertion beyond compilation success.
    // If a new variant is ever added to UiState without updating this function, this
    // test will fail to compile — that is the intended contract.
    // =========================================================================

    @Test
    fun `when expression covers all five UiState branches without else`() {
        fun <T> describeState(state: UiState<T>): String =
            // No `else` branch — compiler enforces all five variants are handled.
            when (state) {
                is UiState.Loading -> "loading"
                is UiState.Empty   -> "empty"
                is UiState.Error   -> "error"
                is UiState.Offline -> "offline"
                is UiState.Ready   -> "ready"
            }

        assertEquals("loading", describeState(UiState.Loading))
        assertEquals("empty",   describeState(UiState.Empty))
        assertEquals("error",   describeState(UiState.Error("E", "m")))
        assertEquals("offline", describeState(UiState.Offline(0L, false)))
        assertEquals("ready",   describeState(UiState.Ready(1)))
    }

    // =========================================================================
    // 7. Offline.hasCache semantics
    // =========================================================================

    @Test
    fun `Offline hasCache is true when constructed with hasCache = true`() {
        val state = UiState.Offline(lastUpdatedMs = 1000L, hasCache = true)

        assertTrue(
            "UiState.Offline.hasCache must be true when constructed with hasCache = true",
            state.hasCache,
        )
    }

    @Test
    fun `Offline hasCache is false when constructed with hasCache = false`() {
        val state = UiState.Offline(lastUpdatedMs = 0L, hasCache = false)

        assertFalse(
            "UiState.Offline.hasCache must be false when constructed with hasCache = false",
            state.hasCache,
        )
    }

    @Test
    fun `Offline lastUpdatedMs stores the provided epoch milliseconds`() {
        val state = UiState.Offline(lastUpdatedMs = 1000L, hasCache = true)

        assertEquals(
            "UiState.Offline.lastUpdatedMs must equal the value passed at construction",
            1000L,
            state.lastUpdatedMs,
        )
    }
}
