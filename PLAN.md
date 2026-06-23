# Feature: MOBILE-26 + MOBILE-27 — Catalog Infrastructure and Access-Check Gate

## Summary

MOBILE-26 introduces a runtime feature-flag registry ("catalog") for the Android app: a
`CatalogApi` + `CatalogRepository` that fetches a flat `{ "flag_name": bool }` JSON map from
`GET /api/catalogs`, caches it in-memory via a `MutableStateFlow`, and exposes a synchronous
`catalogOn(flag)` read. MOBILE-27 uses that infrastructure to gate the body-based access-check
fix behind the flag `gate_access_check_body` inside `AccessRepositoryImpl`, preserving the old
status-based mapping in the `else` branch for instant server-side rollback without an app deploy.

## Approach

**In-process `MutableStateFlow<Map<String,Boolean>>` cache with a repo-owned `CoroutineScope`.**

The repository holds a `CoroutineScope(SupervisorJob() + ioDispatcher)` and starts a
`while (true) { refresh(); delay(POLL_INTERVAL_MS) }` loop from `init {}`. This is chosen over
driving the loop from `BtcApplication.onCreate` because:

- `CatalogRepositoryImpl` is `@Singleton`; it is the correct owner of its own background work.
- The scope lives exactly as long as the process — no Activity or ViewModel tie-in needed.
- Tests inject a `TestCoroutineDispatcher` and advance virtual time to control the loop cleanly.
- `SupervisorJob` ensures a single-cycle failure (e.g., network error) does not cancel the loop.

`POLL_INTERVAL_MS` is a `companion object` `const val` defaulting to `10 * 60 * 1_000L`. Making
the interval server-driven is a follow-up: `GET /api/catalogs` returns `Map<String, Boolean>` and
cannot carry a numeric interval without a shape change on the backend.

`BtcApplication.onCreate` does NOT need a coroutine launch call. Hilt's `@Singleton` scope
constructs `CatalogRepositoryImpl` on first injection (which happens during component init before
`MainActivity` starts), triggering the `init {}` loop automatically.

**Gate implementation (MOBILE-27):** `AccessRepositoryImpl.checkAccess()` reads
`catalogRepository.catalogOn("gate_access_check_body")` synchronously at the top of the
existing `try` block and branches between the new body-based mapping (flag ON) and the old
status-based mapping (flag OFF). Both code paths are preserved in-source.

## Files to Create

- `app/src/main/java/com/gshashank/btcagent/data/network/CatalogApi.kt`
  Retrofit interface with a single method:
  `@GET("api/catalogs") suspend fun getCatalogs(): Map<String, Boolean>`
  The endpoint is public (no auth required). The return type is a raw `Map<String, Boolean>`;
  the kotlinx serialization converter handles a flat JSON object natively when backed by the
  existing `Json { ignoreUnknownKeys = true }` configuration, which tolerates unknown keys and
  shapes. No `Response<>` wrapper — a non-2xx throws `HttpException`, caught by `refresh()`.

- `app/src/main/java/com/gshashank/btcagent/data/repository/CatalogRepository.kt`
  Interface in the `data.repository` package (consistent with `AccessRepository`,
  `AuthRepository`):
  - `suspend fun refresh()` — fetches the flag map and updates the in-memory cache.
    Contract: NEVER throws to callers. On error, keeps the last-known map (FAIL-OPEN).
  - `fun catalogOn(flag: String): Boolean` — synchronous read; returns `false` if the key is
    absent or if the map is empty (first-launch before any successful refresh).

- `app/src/main/java/com/gshashank/btcagent/data/repository/CatalogRepositoryImpl.kt`
  `@Singleton` implementation. Constructor:
  `@Inject constructor(private val catalogApi: CatalogApi, @IoDispatcher private val ioDispatcher: CoroutineDispatcher)`

  Key internals:
  - `private val flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())`
  - `private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)`
  - `init { scope.launch { while (true) { refresh(); delay(POLL_INTERVAL_MS) } } }`
  - `override suspend fun refresh()`:
    `withContext(ioDispatcher)` wrapping a `try { val map = catalogApi.getCatalogs(); flags.value = map }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { /* log; keep flags.value unchanged */ }`
  - `override fun catalogOn(flag: String): Boolean = flags.value[flag] ?: false`
  - `companion object { const val POLL_INTERVAL_MS = 10L * 60 * 1_000 }`

- `app/src/test/java/com/gshashank/btcagent/data/repository/CatalogRepositoryImplTest.kt`
  (see Test Cases section)

## Files to Modify

- `app/src/main/java/com/gshashank/btcagent/di/NetworkModule.kt`
  Add one new `@Provides @Singleton` function after the existing `provideAccessApi`:
  ```
  @Provides
  @Singleton
  fun provideCatalogApi(retrofit: Retrofit): CatalogApi =
      retrofit.create(CatalogApi::class.java)
  ```
  No changes to `provideJson()`, `provideOkHttpClient()`, or `provideRetrofit()`.
  `CatalogApi` reuses the same authenticated `OkHttpClient`-backed `Retrofit` instance. The
  `AuthInterceptor` may attach a Bearer token; the backend ignores it for this public endpoint.
  `TokenAuthenticator` will not fire because the endpoint does not return 401.

- `app/src/main/java/com/gshashank/btcagent/di/RepositoryModule.kt`
  Add one new `@Binds @Singleton` abstract method following the existing pattern:
  ```
  @Binds
  @Singleton
  abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository
  ```
  No changes to the existing `bindAuthRepository`, `bindAccessRepository`, or
  `bindTokenProvider` methods.

- `app/src/main/java/com/gshashank/btcagent/data/repository/AccessRepositoryImpl.kt`
  Two changes:

  1. Add `catalogRepository: CatalogRepository` to the `@Inject constructor`. Full constructor
     after the change:
     `@Inject constructor(private val accessApi: AccessApi, @IoDispatcher private val ioDispatcher: CoroutineDispatcher, private val catalogRepository: CatalogRepository)`

  2. Inside `checkAccess()`, immediately inside the `try` block (after `val response =
     accessApi.checkAccess()`), replace the existing `when` mapping block with a two-branch
     structure:

     ```
     // Flag ON → new body-based mapping (safe, correct behavior)
     // Flag OFF → old status-based mapping (legacy/rollback path)
     if (catalogRepository.catalogOn("gate_access_check_body")) {
         // NEW path (current body-based logic — already in place today)
         when {
             response.code() == 200 -> {
                 val body = response.body()
                 when {
                     body == null -> AccessResult.Error(cause = null)
                     body.allowed -> AccessResult.Allowed(admin = body.admin)
                     else -> AccessResult.Pending
                 }
             }
             response.code() == 401 -> AccessResult.Unauthorized
             else -> AccessResult.Error(cause = null)
         }
     } else {
         // OLD path (status-based — preserved for instant rollback)
         when (response.code()) {
             200 -> AccessResult.Allowed(admin = false)
             401 -> AccessResult.Unauthorized
             else -> AccessResult.Error(cause = null)
         }
     }
     ```

     The `CancellationException` re-throw and outer `catch (e: Exception)` remain unchanged and
     wrap both branches. No other logic in the file changes.

- `app/src/test/java/com/gshashank/btcagent/data/repository/AccessRepositoryImplTest.kt`
  Two changes:

  1. Add a private `FakeCatalogRepository` class at the bottom of the test file:
     ```
     // (specification only — do not implement here)
     // flagOn = true by default so all existing body-based test cases pass unchanged
     private class FakeCatalogRepository(var flagOn: Boolean = true) : CatalogRepository {
         override suspend fun refresh() = Unit
         override fun catalogOn(flag: String): Boolean = flagOn
     }
     ```

  2. In `setUp()`, change the `AccessRepositoryImpl` construction to pass the fake:
     ```
     repository = AccessRepositoryImpl(
         accessApi = accessApi,
         ioDispatcher = testDispatcher,
         catalogRepository = FakeCatalogRepository(flagOn = true),
     )
     ```
     All six existing test cases remain unchanged (they exercise the flag-ON / body-based path).
     Add the three new flag-OFF test cases listed in Test Cases below.

## Test Cases to Write

### `CatalogRepositoryImplTest` (new file, unit tests, MockWebServer + coroutines-test)

Test infrastructure mirrors `AccessRepositoryImplTest`: real `MockWebServer`, real `Retrofit`
with `Json { ignoreUnknownKeys = true }` + kotlinx serialization converter, `OkHttpClient()`,
`UnconfinedTestDispatcher` substituted for `@IoDispatcher`.

Construct `CatalogRepositoryImpl` directly (no Hilt in unit tests). Note: the `init {}` polling
loop fires immediately on construction with `UnconfinedTestDispatcher`; tests should enqueue at
least one server response before constructing the repo, or call `refresh()` manually and skip
init-loop side effects by using `TestScope.advanceUntilIdle()`.

- **`getCatalogs parses true and false flags correctly`** — enqueue
  `{"a":true,"b":false}`; call `refresh()`; assert `catalogOn("a") == true` and
  `catalogOn("b") == false`.

- **`catalogOn returns false for a missing flag`** — enqueue `{"x":true}`; call `refresh()`;
  assert `catalogOn("missing") == false`.

- **`refresh failure keeps last-known map and never throws`** — enqueue a valid response
  `{"a":true}`; call `refresh()`; assert `catalogOn("a") == true`; shut down the server;
  call `refresh()` again; assert no exception is propagated to the caller AND
  `catalogOn("a") == true` (map is unchanged by the failed refresh).

- **`initial state before any refresh returns all-false`** — construct the repo with the server
  returning nothing yet (do not call `refresh()`; or construct in isolation without running
  the init loop); assert `catalogOn("anything") == false` (the initial `emptyMap()` default).

- **`refresh with HTTP error response keeps last-known map`** — enqueue a good `{"flag":true}`
  response; call `refresh()`; then enqueue an HTTP 500 response; call `refresh()` again;
  assert `catalogOn("flag") == true` (the failed call did not clear the map).

### `AccessRepositoryImplTest` (extend existing file)

All six existing tests remain. Add three new flag-OFF tests. Each test constructs a local
`FakeCatalogRepository(flagOn = false)` and a fresh `AccessRepositoryImpl` instance (or sets
a field on the shared fake before running — use whichever pattern matches the setUp approach).

- **`flag OFF and 200 maps to Allowed(admin=false) regardless of body`** — `flagOn = false`;
  enqueue HTTP 200 with body `{"allowed":false,"admin":false}`; call `checkAccess()`; assert
  `AccessResult.Allowed(admin = false)`. This is the critical regression test for the rollback
  path: under the OLD behavior any 200 was Allowed.

- **`flag OFF and 401 maps to Unauthorized`** — `flagOn = false`; enqueue HTTP 401; assert
  `AccessResult.Unauthorized`.

- **`flag OFF and 500 maps to Error`** — `flagOn = false`; enqueue HTTP 500; assert
  `result is AccessResult.Error`.

## Risks / Assumptions

**[CRITICAL] Security-sensitive gate default direction.**

The catalog INVARIANT is: missing flag = code default `false` = OLD behavior. For
`gate_access_check_body`, the OLD behavior is the buggy status-based path: any HTTP 200 from
the access endpoint grants `Allowed(admin=false)` regardless of the response body. Therefore:

If `GET /api/catalogs` is unreachable on the very first app launch (network failure, cold start
race), `flags.value` is `emptyMap()`, `catalogOn("gate_access_check_body")` returns `false`,
and any authenticated user gets `Allowed(admin=false)` — the access gate is bypassed.

Under normal conditions this window is narrow: login flow is sequential (sign in → Gate screen →
access check), and the first `refresh()` in the polling loop fires before `checkAccess()` is
called. However, this ordering is not enforced by the code.

**Three options — the implementer/team must choose one before implementation:**

- **Option A (recommended): Invert the code default for this specific flag.**
  In `AccessRepositoryImpl`, treat the absence of `gate_access_check_body` as `true` (ON) rather
  than `false` (OFF). This means replacing `catalogRepository.catalogOn("gate_access_check_body")`
  with `catalogRepository.catalogOn("gate_access_check_body") != false` is equivalent to the same
  thing, but more clearly: define a helper or inline `flags.value.getOrDefault("gate_access_check_body", true)`.
  This breaks the catalog INVARIANT locally for this one call site but is the safest choice:
  when the catalog cannot be read, the safer (body-based) path is used. The flag can still be
  flipped OFF server-side for rollback. Document the local inversion clearly in code comments.

- **Option B: Await `refresh()` before the access check.**
  Have `GateViewModel.checkAccess()` call `catalogRepository.refresh()` (suspended, awaited)
  before calling `accessRepository.checkAccess()`. This enforces sequencing at the cost of one
  extra network round-trip on every Gate screen load (including retries). Note: if `refresh()`
  itself fails (network unreachable), this still falls back to the buggy path — so Option B is
  weaker than Option A for security, but is correct under partial failures.

- **Option C: Hard-code a safe seed in `CatalogRepositoryImpl`.**
  On the very first call to `refresh()`, if it fails, seed `flags.value` with
  `mapOf("gate_access_check_body" to true)` as a process-level default. Subsequent failures
  preserve last-known. This is surgical but couples the infrastructure layer to a specific flag
  name, which is a leaky abstraction.

**Recommendation: Option A.** It has no runtime cost, no sequencing requirement, and the safest
fallback. The local inversion must be clearly documented in `AccessRepositoryImpl` with a comment
explaining the asymmetry.

**`CatalogApi` uses the authenticated `OkHttpClient`.**
A single `Retrofit` instance is shared across all API interfaces. `AuthInterceptor` may attach a
Bearer token to catalog requests; the backend ignores it (the endpoint is public). The
`TokenAuthenticator` will not fire because the backend does not return 401 for this endpoint.
This is safe and requires no special handling or a second `Retrofit` instance.

**No new Gradle dependencies.**
`CatalogApi` uses Retrofit + kotlinx serialization converter already in the dependency graph.
`Map<String, Boolean>` is a standard Kotlin type handled natively by the existing converter.
`MockWebServer`, `kotlinx-coroutines-test`, and `mockito-kotlin` are already in
`testImplementation`. No `libs.versions.toml` changes are required.

**Repo-owned `CoroutineScope` and process lifetime.**
`CoroutineScope(SupervisorJob() + ioDispatcher)` is never explicitly cancelled. In production
this is correct — the `@Singleton` repo lives for the process lifetime. In unit tests, the
injected `TestCoroutineScope` is cancelled by `runTest` at completion, tearing down the loop
cleanly. There is no leak risk.

**`retrofit2.HttpException` is a subclass of `RuntimeException`, not `IOException`.**
The `catch (e: Exception)` in `refresh()` correctly catches both `HttpException` (non-2xx HTTP
status) and `IOException` (network failure), so the FAIL-OPEN contract holds for all error
modes. `CancellationException` must be re-thrown before the broad catch to avoid swallowing
coroutine cancellation — this is already the pattern used in `AccessRepositoryImpl`.

**Polling loop fires on `init {}` with `UnconfinedTestDispatcher`.**
`UnconfinedTestDispatcher` runs coroutines eagerly. When `CatalogRepositoryImpl` is constructed
in a test, the `init {}` launch will execute `refresh()` immediately (before the test body can
enqueue a server response). Tests should either: (a) enqueue a default response before
construction, or (b) use `StandardTestDispatcher` and advance the clock explicitly, or (c)
call `refresh()` manually on a separately constructed instance (bypassing the loop). The test
cases above assume approach (a) or (b) — the implementer should pick consistently.

✅ Plan ready. Review PLAN.md and run /project:implement to proceed.
