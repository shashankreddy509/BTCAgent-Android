# Feature: Pending-approval / Gate Screen (Jira MOBILE-4)

## Summary
After a successful Google sign-in, this feature inserts an access-gate between Login and Home. The gate calls a backend allow-list endpoint (`GET /api/access/check`) and routes the user to Home (200/allowed), a Pending-approval screen (403/pending), or back to Login (401/unauthorized or sign-out). It also introduces the auth networking layer — `AuthInterceptor` and `TokenAuthenticator` — that all future authenticated API calls will reuse.

## Approach
The feature is split into three cooperating layers introduced in dependency order: (1) auth networking infrastructure (`TokenProvider` → `AuthInterceptor` → `TokenAuthenticator` wired into `OkHttpClient`), (2) access data layer (`AccessApi` → `AccessRepository` mapping HTTP codes to a sealed result type), and (3) Gate UI layer (`Route.Gate`, `GateViewModel`, `GateContent`/`GateScreen` following the existing stateless-Content + stateful-Screen convention established in `LoginScreen.kt`).

The key architectural decision for the auth interceptor is a **cached `TokenProvider`** interface backed by `AuthRepository.getIdToken()`. OkHttp interceptors are called on OkHttp's synchronous thread pool, so a `runBlocking` wrapper is unavoidable; keeping it in a dedicated `TokenProvider` implementation makes the `AuthInterceptor` thin and independently unit-testable with a `FakeTokenProvider` against `MockWebServer` — no real Firebase needed. The `TokenAuthenticator` checks `response.priorResponses.isNotEmpty()` before retrying to guarantee the retry happens at most once and the loop cannot occur.

`AccessApi` uses `retrofit2.Response<Unit>` (not a deserialised body type) so the HTTP status code is directly readable without a custom converter — matching the idiomatic pattern for status-only endpoints.

## Files to Create

### Auth Networking Layer

- `app/src/main/java/com/gshashank/btcagent/data/network/TokenProvider.kt`
  A minimal interface with a single blocking method: `fun getToken(): String?`. Being a plain interface (not a suspend function) makes it directly callable from OkHttp's synchronous interceptor thread. A `FakeTokenProvider` in tests simply returns a hardcoded string or `null`.

- `app/src/main/java/com/gshashank/btcagent/data/network/FirebaseTokenProvider.kt`
  `@Singleton` implementation of `TokenProvider`. Injected with `AuthRepository`. `getToken()` calls `runBlocking { authRepository.getIdToken() }.getOrNull()`. The `runBlocking` scope is intentionally narrow — only the token fetch, not the full request. Annotated with `@Inject constructor`.

- `app/src/main/java/com/gshashank/btcagent/data/network/AuthInterceptor.kt`
  Implements `okhttp3.Interceptor`. Injected with `TokenProvider`. In `intercept()`: calls `tokenProvider.getToken()`; if non-null, adds `Authorization: Bearer <token>` to the request chain and proceeds; if null, proceeds without the header (the server returns 401, which the `TokenAuthenticator` handles). Annotated with `@Inject constructor` and `@Singleton`.

- `app/src/main/java/com/gshashank/btcagent/data/network/TokenAuthenticator.kt`
  Implements `okhttp3.Authenticator`. Injected with `AuthRepository`. In `authenticate()`: if `response.priorResponses.isNotEmpty()`, return `null` immediately (give up — prevents infinite retry loop). Otherwise call `runBlocking { authRepository.getIdToken() }` (which already uses `forceRefresh = true` internally). If the token is obtained successfully, rebuild the request with the refreshed `Authorization: Bearer <token>` header and return it. If token fetch fails, return `null`. Annotated with `@Inject constructor` and `@Singleton`.

### Access Data Layer

- `app/src/main/java/com/gshashank/btcagent/data/network/AccessApi.kt`
  Retrofit interface with a single method: `@GET("api/access/check") suspend fun checkAccess(): retrofit2.Response<Unit>`. Using `Response<Unit>` avoids body deserialization and surfaces the HTTP status code directly.

- `app/src/main/java/com/gshashank/btcagent/data/repository/AccessResult.kt`
  Sealed interface with four variants: `data object Allowed`, `data object Pending`, `data object Unauthorized`, `data class Error(val cause: Throwable?)`. Lives alongside `AuthRepository.kt` in the repository package.

- `app/src/main/java/com/gshashank/btcagent/data/repository/AccessRepository.kt`
  Interface with a single method: `suspend fun checkAccess(): AccessResult`.

- `app/src/main/java/com/gshashank/btcagent/data/repository/AccessRepositoryImpl.kt`
  `@Singleton` implementation of `AccessRepository`. Injected with `AccessApi` and `@IoDispatcher CoroutineDispatcher`. In `checkAccess()`: wraps the call in `withContext(ioDispatcher) { try { ... } catch (e: IOException) { AccessResult.Error(e) } }`. Maps `response.code()`: 200 → `Allowed`, 403 → `Pending`, 401 → `Unauthorized`, anything else → `Error(null)`. Annotated with `@Inject constructor`.

### Gate UI Layer

- `app/src/main/java/com/gshashank/btcagent/ui/gate/GateUiState.kt`
  Sealed interface mirroring the LoginUiState convention: `data object Loading`, `data object Allowed`, `data class Pending(val email: String)`, `data object Unauthorized`, `data object Error`.

- `app/src/main/java/com/gshashank/btcagent/ui/gate/GateViewModel.kt`
  `@HiltViewModel`. Injected with `AccessRepository`, `AuthRepository`, and `@MainDispatcher CoroutineDispatcher`. Exposes `val uiState: StateFlow<GateUiState>` initialised to `Loading`. Uses the same injected-scope pattern as `LoginViewModel`: `CoroutineScope(SupervisorJob() + mainDispatcher)` cancelled in `onCleared()`. On `init`, launches `checkAccess()` which calls `accessRepository.checkAccess()` and maps: `Allowed` → `GateUiState.Allowed`, `Pending` → `GateUiState.Pending(authRepository.currentUser?.email ?: "")`, `Unauthorized` → `GateUiState.Unauthorized`, `Error` → `GateUiState.Error`. Exposes `fun onRetry()` (resets state to `Loading`, re-runs `checkAccess()`) and `fun onSignOut()` (calls `authRepository.signOut()`, then emits `Unauthorized`).

- `app/src/main/java/com/gshashank/btcagent/ui/gate/GateScreen.kt`
  Two composables in one file, following the `LoginScreen.kt` convention exactly:

  `GateContent(uiState: GateUiState, onSignOut: () -> Unit, onRetry: () -> Unit)` — stateless. Renders per-state UI. For the `Pending` state (the primary design spec, screen 02):
  - Full-height centered `Box` with `background(MaterialTheme.colorScheme.background)` — no top bar, no `Scaffold`.
  - 84dp ring: a `Box(Modifier.size(84.dp))` containing a `CircularProgressIndicator` filling the box (stroke 3.dp, `color = MaterialTheme.colorScheme.primary`). This creates the accent spinner ring. Apply `testTag("gate_spinner")`.
  - Spacer(16.dp).
  - Headline `"Waiting for approval"`: `MaterialTheme.typography.headlineSmall` overridden to 21sp / `FontWeight.SemiBold`, `color = MaterialTheme.colorScheme.onBackground`. Apply `testTag("gate_headline")`.
  - Spacer(8.dp).
  - Email body text from `(uiState as GateUiState.Pending).email`: `bodyMedium` 14sp, `onBackground` at 56% alpha. Apply `testTag("gate_email")`.
  - Spacer(16.dp).
  - Status chip: `Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline))`. Inside: `Row` with a 6dp `Box` dot (`background = MaterialTheme.colorScheme.primary`, `CircleShape`, alpha animated via `rememberInfiniteTransition` between 0.3f and 1.0f using `animateFloat` with `InfiniteRepeatableSpec(tween(800), RepeatMode.Reverse)`) and `Text("Pending review", style = labelSmall)`. Apply `testTag("gate_status_chip")` on the Surface.
  - `Spacer(Modifier.weight(1f))` (push sign-out to bottom).
  - `TextButton(onClick = onSignOut, modifier = Modifier.testTag("gate_sign_out"))` with label `"Sign out"`, `color = MaterialTheme.colorScheme.onBackground`.

  For `Loading` state: centered `CircularProgressIndicator(Modifier.testTag("gate_loading_indicator"))`.
  For `Error` state: centered `Column` with error message text and `Button("Retry", onClick = onRetry, modifier = Modifier.testTag("gate_retry_button"))`.
  For `Allowed` and `Unauthorized` states: render nothing (a plain empty `Box`) — the `LaunchedEffect` in `GateScreen` handles the navigation side-effect; no UI content is needed.

  `GateScreen(viewModel: GateViewModel, onAllowed: () -> Unit, onSignedOut: () -> Unit)` — stateful wrapper. Collects `viewModel.uiState` with `collectAsStateWithLifecycle()`. Uses `LaunchedEffect(uiState)` to fire `onAllowed()` when state is `Allowed` and `onSignedOut()` when state is `Unauthorized`. Passes `uiState`, `viewModel::onSignOut`, and `viewModel::onRetry` down to `GateContent`.

### Test Files

- `app/src/test/java/com/gshashank/btcagent/data/network/AuthInterceptorTest.kt`
  JVM unit tests for `AuthInterceptor` and `TokenAuthenticator` using `MockWebServer`.

- `app/src/test/java/com/gshashank/btcagent/data/repository/AccessRepositoryImplTest.kt`
  JVM unit tests for `AccessRepositoryImpl` using `MockWebServer` (or a `FakeAccessApi`).

- `app/src/test/java/com/gshashank/btcagent/ui/gate/GateViewModelTest.kt`
  JVM unit tests for `GateViewModel` using `FakeAccessRepository` + `FakeAuthRepository` + Turbine.

- `app/src/androidTest/java/com/gshashank/btcagent/ui/gate/GateContentTest.kt`
  Compose instrumented UI tests for `GateContent`.

## Files to Modify

- `app/src/main/java/com/gshashank/btcagent/di/NetworkModule.kt`
  Three targeted changes, no existing provider signatures altered:
  1. In `provideOkHttpClient()`: add `authInterceptor: AuthInterceptor` and `tokenAuthenticator: TokenAuthenticator` as injected parameters. Inside the builder, add `.redactHeader("Authorization")` to the `HttpLoggingInterceptor` apply block (prevents tokens appearing in Logcat). Add `.addInterceptor(authInterceptor)` after the logging interceptor. Add `.authenticator(tokenAuthenticator)`.
  2. Add a new `@Provides @Singleton fun provideAccessApi(retrofit: Retrofit): AccessApi = retrofit.create(AccessApi::class.java)`. This follows the same pattern already used for `provideRetrofit` and keeps all network providers in one module.
  3. No changes to `provideJson()` or `provideRetrofit()`.

- `app/src/main/java/com/gshashank/btcagent/di/RepositoryModule.kt`
  Add one new `@Binds @Singleton` abstract method: `abstract fun bindAccessRepository(impl: AccessRepositoryImpl): AccessRepository`. Also add a `@Binds @Singleton` abstract method: `abstract fun bindTokenProvider(impl: FirebaseTokenProvider): TokenProvider` so `AuthInterceptor` and `TokenAuthenticator` receive the Firebase-backed implementation. The existing `bindAuthRepository` binding is untouched. (If the `TokenProvider` binding feels out-of-place in `RepositoryModule` given that `TokenProvider` is a networking concern, the implementer may create a new `app/src/main/java/com/gshashank/btcagent/di/NetworkBindingsModule.kt` abstract module instead — either is acceptable; the binding must exist somewhere.)

- `app/src/main/java/com/gshashank/btcagent/ui/navigation/AppRoutes.kt`
  Add `@Serializable data object Gate : Route` inside the existing `Route` sealed interface. `Login` and `Home` are untouched.

- `app/src/main/java/com/gshashank/btcagent/MainActivity.kt`
  Two changes inside `AppNavHost()`:
  1. In `composable<Route.Login>`, change the `onAuthenticated` lambda body from navigating to `Route.Home` to navigating to `Route.Gate`: `navController.navigate(Route.Gate) { popUpTo(Route.Login) { inclusive = true } }`.
  2. Add a new `composable<Route.Gate>` entry wiring `GateScreen(viewModel = hiltViewModel(), onAllowed = { navController.navigate(Route.Home) { popUpTo(Route.Gate) { inclusive = true } } }, onSignedOut = { navController.navigate(Route.Login) { popUpTo(0) { inclusive = true } } })`. The `popUpTo(0)` on sign-out clears the entire back stack so back-press from Login cannot navigate back to Gate or Home.

## Test Cases to Write

### `AuthInterceptorTest` (JVM, MockWebServer + FakeTokenProvider)
- **Bearer header is attached when token is present**: configure `FakeTokenProvider` to return `"test-token"`; enqueue a 200 response; execute any request; assert the recorded request has header `Authorization: Bearer test-token`.
- **No Authorization header when token is null**: configure `FakeTokenProvider` to return `null`; assert the recorded request does not contain an `Authorization` header.
- **401 triggers exactly one forced-refresh retry and succeeds**: enqueue a 401 then a 200; `TokenAuthenticator` retries once with a refreshed token; assert `mockWebServer.requestCount == 2` and the final response code is 200.
- **Second 401 after retry gives up — no infinite loop**: enqueue three 401 responses; assert `mockWebServer.requestCount == 2` (interceptor tried once, authenticator retried once, then gave up); the final call result is a 401 response (not an exception).
- **Authorization header is redacted in logs**: configure `HttpLoggingInterceptor.Level.HEADERS`; execute a request; assert captured log output does not contain the literal token string (the `redactHeader("Authorization")` contract).

### `AccessRepositoryImplTest` (JVM, MockWebServer)
- **200 response maps to `AccessResult.Allowed`**: enqueue HTTP 200; call `checkAccess()`; assert result is `Allowed`.
- **403 response maps to `AccessResult.Pending`**: enqueue HTTP 403; assert result is `Pending`.
- **401 response maps to `AccessResult.Unauthorized`**: enqueue HTTP 401; assert result is `Unauthorized`.
- **500 response maps to `AccessResult.Error`**: enqueue HTTP 500; assert result is `Error`.
- **IOException maps to `AccessResult.Error` with non-null cause**: shut down MockWebServer before the call; assert result is `Error` with a non-null `cause`. Use `UnconfinedTestDispatcher` as the `@IoDispatcher` substitute in all cases.

### `GateViewModelTest` (JVM, FakeAccessRepository + FakeAuthRepository + Turbine)
Use the same `FakeAuthRepository` structure from `LoginViewModelTest` (extend it or copy; it already has `currentUser`, `getIdToken`, `signOut`). Add a `FakeAccessRepository` with a configurable `checkAccessResult: AccessResult`. Use `UnconfinedTestDispatcher` as the `@MainDispatcher` substitute.

- **init emits Loading then Allowed**: `fakeAccess.checkAccessResult = AccessResult.Allowed`; collect via Turbine; assert emissions are `[Loading, Allowed]` in order.
- **init emits Loading then Pending with email**: `fakeAccess.checkAccessResult = AccessResult.Pending`; `fakeAuth.currentUser.email = "user@example.com"`; assert emissions are `[Loading, Pending("user@example.com")]`.
- **init emits Loading then Unauthorized**: `fakeAccess.checkAccessResult = AccessResult.Unauthorized`; assert `[Loading, Unauthorized]`.
- **init emits Loading then Error**: `fakeAccess.checkAccessResult = AccessResult.Error(null)`; assert `[Loading, Error]`.
- **onRetry resets to Loading and re-runs check**: starting from `Error`, call `onRetry()`; Turbine observes `Loading` again followed by the new result.
- **onSignOut calls authRepository.signOut and emits Unauthorized**: call `onSignOut()`; assert `fakeAuth.signOutCalled == true` and final state is `Unauthorized`.
- **Pending email is empty string when currentUser is null**: `fakeAccess.checkAccessResult = AccessResult.Pending`; `fakeAuth.currentUser = null`; assert state is `Pending("")`.

### `GateContentTest` (Compose instrumented, `ComposeTestRule`)
Wrap `GateContent` in `BTCAgentTheme` for each test using `createComposeRule()`.

- **Pending state renders all required elements**: set `uiState = GateUiState.Pending("test@example.com")`; assert nodes with `testTag("gate_spinner")`, `testTag("gate_headline")`, `testTag("gate_email")`, `testTag("gate_status_chip")`, `testTag("gate_sign_out")` all exist and are displayed.
- **Headline text is correct**: node with `testTag("gate_headline")` has text `"Waiting for approval"`.
- **Email text matches the Pending state email**: node with `testTag("gate_email")` has text `"test@example.com"`.
- **Sign out click invokes callback**: perform click on `testTag("gate_sign_out")`; assert `onSignOutCalled == true`.
- **Loading state shows loading indicator only**: set `uiState = GateUiState.Loading`; assert `testTag("gate_loading_indicator")` exists; assert `testTag("gate_headline")` does not exist.
- **Error state shows retry button**: set `uiState = GateUiState.Error`; assert `testTag("gate_retry_button")` exists; perform click; assert `onRetryCalled == true`.

## Risks / Assumptions

1. **`AuthRepository.getIdToken()` always force-refreshes**: The current `AuthRepositoryImpl.getIdToken()` always passes `forceRefresh=true` to Firebase. This is correct for `TokenAuthenticator` (forced refresh on 401) but slightly expensive for `AuthInterceptor` (every outbound request triggers a Firebase token validation call even when the cached token is still valid). Firebase SDK internally caches and throttles token refreshes so this is acceptable for Phase 1. If performance becomes a concern, a `getIdToken(forceRefresh: Boolean)` overload should be added to the `AuthRepository` interface — the `AuthInterceptor` would pass `false`, the `TokenAuthenticator` would pass `true`. Flag this as a follow-up, not a blocker.

2. **`runBlocking` in OkHttp threads**: Using `runBlocking` inside `AuthInterceptor.intercept()` and `TokenAuthenticator.authenticate()` is the only practical approach given OkHttp's synchronous interceptor contract. The `runBlocking` block only wraps the Firebase token fetch (which runs on `@IoDispatcher` inside `AuthRepositoryImpl`). Deadlock risk exists only if OkHttp is called from the main thread with `Dispatchers.Main` blocking — this does not occur in this app (all Retrofit calls are made from `viewModelScope` or `@IoDispatcher` coroutines).

3. **Sign-out back-stack clearing**: The `onSignedOut` lambda uses `popUpTo(0) { inclusive = true }` to clear the entire back stack before navigating to Login. This ensures back-press from Login cannot return to Gate or Home. If future features add pre-auth routes (e.g., an onboarding flow), confirm that `popUpTo(0)` is still the correct clearing strategy (it pops everything back to the NavGraph root, which is Login after the clearing, so it remains correct).

4. **`TokenProvider` binding location**: `FirebaseTokenProvider` implements a networking-layer interface but `RepositoryModule` currently hosts all `@Binds` declarations. The implementer may add the `TokenProvider` binding to `RepositoryModule` for simplicity or create a new `di/NetworkBindingsModule.kt` for separation of concerns. Either compiles correctly — the choice should be noted in the commit message.

5. **`AccessApi` in `NetworkModule`**: Placing `provideAccessApi` in `NetworkModule` is appropriate now (one Retrofit instance, one API interface). When the number of API interfaces grows (e.g., `PriceApi`, `TradeApi`), extract all `@Provides fun provideXyzApi(retrofit)` methods into a dedicated `ApiModule`. That refactor is mechanical and deferred.

6. **No new Gradle dependencies required**: `okhttp-mockwebserver` (5.1.0), `turbine` (1.2.1), `kotlinx-coroutines-test` (1.10.2), and `mockito-kotlin` (5.4.0) are already declared in `libs.versions.toml` and in the `testImplementation` block of `app/build.gradle.kts`. No version bumps or new catalog entries are needed for this feature.

7. **Pulsing dot animation**: `rememberInfiniteTransition` and `animateFloat` are available from `androidx.compose.animation` which is already transitively included via the Compose BOM. No additional animation dependency is required.

8. **`GateUiState.Allowed` / `Unauthorized` UI content**: The plan specifies an empty `Box` for these states in `GateContent` because navigation is triggered by `LaunchedEffect` in `GateScreen`. The risk is a brief visual flash of blank content before the navigation completes. If this is visually unacceptable during testing, render the `Loading` indicator for these transient states instead.

✅ Plan ready. Review PLAN.md and run /project:implement to proceed.
