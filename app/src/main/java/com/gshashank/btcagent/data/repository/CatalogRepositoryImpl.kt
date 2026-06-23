package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.CatalogApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton implementation of [CatalogRepository].
 *
 * Owns a repo-level [CoroutineScope] backed by [ioDispatcher] + [SupervisorJob]. On construction
 * (`init {}`), a polling loop is launched: `while (isActive) { refresh(); delay(POLL_INTERVAL_MS) }`.
 *
 * Flags are stored in a [MutableStateFlow]<Map<String, Boolean>> seeded with an empty map.
 * [catalogOn] reads the current snapshot synchronously.
 *
 * The init-loop is safe under [kotlinx.coroutines.test.StandardTestDispatcher]: that dispatcher
 * will not run the launched coroutine until the test clock is explicitly advanced, so construction
 * never blocks or consumes mock responses eagerly. Tests call [refresh] manually instead.
 */
@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val catalogApi: CatalogApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CatalogRepository {

    companion object {
        /** Background poll interval. Exposed as a constant so tests can reference it. */
        const val POLL_INTERVAL_MS = 10L * 60 * 1_000
    }

    private val flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * Repo-owned scope. SupervisorJob ensures a single-cycle network failure does not cancel the
     * loop; the scope lives for the process lifetime (same as the @Singleton).
     */
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Fetches the flag map from the server and atomically replaces the in-memory cache.
     *
     * FAIL-OPEN: any exception (network, HTTP, parse) is swallowed AFTER re-throwing
     * [CancellationException] to avoid breaking coroutine cancellation. On failure the last-known
     * map is preserved. First-ever failure leaves the map empty (all flags OFF).
     */
    override suspend fun refresh(): Unit = withContext(ioDispatcher) {
        try {
            // Defensive copy: the deserialized map is a mutable LinkedHashMap; store an
            // immutable snapshot so no interop path can mutate cached flags in place.
            flags.value = catalogApi.getCatalogs().toMap()
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            // Fail-open: keep last-known map, never throw to caller.
            // Intentional no-op: network errors, HTTP errors, and parse failures all leave
            // flags.value unchanged so existing app behaviour is preserved.
        }
    }

    /**
     * Returns the current value of [flag] from the in-memory cache.
     *
     * Synchronous, non-blocking. Returns `false` when [flag] is absent or no successful
     * refresh has occurred yet (initial empty map).
     */
    override fun catalogOn(flag: String): Boolean = flags.value[flag] ?: false

    /**
     * Returns the current value of [flag] from the in-memory cache, using [default] when the
     * key is absent entirely.
     *
     * This overload is the mechanism for Option-A: callers that want a safe fall-through for a
     * missing key can pass `default = true` to treat absence as ON rather than the catalog
     * invariant default of OFF. See [AccessRepositoryImpl] for the security-sensitive usage.
     */
    override fun catalogOn(flag: String, default: Boolean): Boolean =
        flags.value.getOrDefault(flag, default)
}
