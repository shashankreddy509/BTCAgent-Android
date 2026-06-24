package com.gshashank.btcagent.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gshashank.btcagent.BuildConfig
import com.gshashank.btcagent.data.network.CatalogApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val catalogApi: CatalogApi,
    // Project Json (ignoreUnknownKeys = true): a bare Json companion would throw on any future
    // persisted-schema addition, wiping flags to empty on cold-start decode.
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CatalogRepository {

    companion object {
        const val POLL_INTERVAL_MS = 10L * 60 * 1_000
        private val KEY_MAP_JSON = stringPreferencesKey("catalog_map_json")
        private val KEY_VERSION = intPreferencesKey("catalog_version")
        private const val PLATFORM_ANDROID = 1
    }

    private val flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // @Volatile: written by the seed coroutine and refresh(), read by refresh(), all on the
    // multi-threaded IO pool — needs a happens-before guarantee to avoid stale version reads.
    @Volatile
    private var cachedVersion: Int = 0

    // SupervisorJob: a thrown child must not cancel the sibling poll loop (process-lifetime scope).
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        // Defensive backstop: the security-sensitive USER_ACCESS_STATUS gate must have a real
        // allocated id (currently 100002). Id 0 is the "unallocated placeholder" sentinel — with
        // it the server cannot push a rollback (isEnabled(0,default=true) is always the safe path,
        // so the kill-switch would be inert). Fail fast on a release build if anyone ever reverts
        // it to 0; debug builds tolerate the placeholder during early development of a new gate.
        if (!BuildConfig.DEBUG) {
            check(CatalogFlags.USER_ACCESS_STATUS != 0) {
                "USER_ACCESS_STATUS is the unallocated placeholder id 0 — the access-gate rollback " +
                    "kill-switch is inert. Set the real allocated id before a release build."
            }
        }

        // Seed the in-memory map from persisted DataStore so isEnabled() returns last-known-good
        // immediately on a warm start. The FIRST network fetch is triggered explicitly by
        // BtcApplication.onCreate (via refresh()), NOT here — keeping init free of network I/O
        // makes the repo deterministic under test (tests construct it and drive refresh() manually).
        scope.launch {
            try {
                val prefs = dataStore.data.first()
                val jsonStr = prefs[KEY_MAP_JSON]
                val version = prefs[KEY_VERSION] ?: 0
                if (!jsonStr.isNullOrBlank()) {
                    flags.value = json.decodeFromString(jsonStr)
                }
                cachedVersion = version
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: Exception) {
                // Fall back to empty map; all flags OFF / Option-A default honored.
            }
        }

        // Background poll loop. The leading delay means this loop does NOT perform the startup
        // fetch (BtcApplication does that explicitly); it only keeps the catalog fresh thereafter.
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refresh()
            }
        }
    }

    override suspend fun refresh() {
        try {
            val response = withContext(ioDispatcher) {
                catalogApi.getCatalogs(platform = PLATFORM_ANDROID, version = cachedVersion)
            }
            // changed:true with a present catalogs snapshot → replace whole map (never merge).
            // A null catalogs on changed:true is malformed; keep last-known-good rather than wipe.
            if (response.changed && response.catalogs != null) {
                val newMap = response.catalogs
                // Update in-memory first so isEnabled() reflects the new snapshot even if the
                // DataStore write is slow; persistence is best-effort durability after that.
                flags.value = newMap
                cachedVersion = response.version
                val jsonStr = json.encodeToString(newMap)
                dataStore.edit { prefs ->
                    prefs[KEY_MAP_JSON] = jsonStr
                    prefs[KEY_VERSION] = response.version
                }
            }
            // changed == false (or malformed null catalogs) → no-op, keep last-known-good
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Swallow; keep last-known map intact. Fail-open / last-known-good.
        }
    }

    override fun isEnabled(id: Int): Boolean = flags.value[id.toString()] ?: false

    override fun isEnabled(id: Int, default: Boolean): Boolean =
        flags.value.getOrDefault(id.toString(), default)

    override fun isEnabledFlow(id: Int, default: Boolean): Flow<Boolean> =
        flags
            .map { it.getOrDefault(id.toString(), default) }
            .distinctUntilChanged()
}
