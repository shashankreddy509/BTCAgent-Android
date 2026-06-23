# Feature: Login Screen

## Summary
Implement a Bitcoin-themed Login screen for the BTCAgent app that presents a dark-first Material 3 colour system, a pixel-faithful Google sign-in UI, and real Credential Manager → FirebaseAuth integration. The screen follows the project's stateless-Content + state-holder composable convention and is wired into a minimal Navigation-Compose `NavHost` hosted in `MainActivity`.

## Approach
The feature is split into three orthogonal layers that must land in dependency order:

1. **Theme layer** — overwrite the default purple template colour/type/theme files with Bitcoin tokens. All subsequent Compose work depends on these tokens being correct first.
2. **Data layer** — `AuthRepository` interface + `AuthRepositoryImpl` using AndroidX Credential Manager. The repository is bound in the existing empty `RepositoryModule` via `@Binds`. This layer has zero Compose dependency, making it independently unit-testable.
3. **UI layer** — `LoginUiState`, `LoginViewModel`, `LoginScreen`/`LoginContent` composables, a `NavHost` in `MainActivity`. Depends on both layers above.

**Font strategy — downloadable Google Fonts via `androidx.compose.ui:ui-text-google-fonts`:** The AndroidX downloadable fonts provider resolves Roboto Flex and Roboto Mono at runtime from the Play Services font cache, requiring zero bundled `.ttf` assets and no APK size increase. The alternative (bundled `.ttf`) requires adding ~1.5 MB of assets and manual `FontVariation` configuration for variable axes. Downloadable fonts is the recommended path for production apps targeting Play distribution and is the simpler implementation.

**Credential Manager vs legacy `GoogleSignIn` SDK:** The modern `androidx.credentials` API (Credential Manager + `GetGoogleIdOption`) is the Google-recommended replacement for the deprecated `GoogleSignIn` client. It surfaces the bottom-sheet account picker natively and handles one-tap and passkeys in a unified API. This is the correct choice for a new app targeting minSdk 24+.

**Navigation — type-safe routes with `kotlinx.serialization`:** The version catalog already has `kotlin-serialization` applied. The Navigation-Compose version (`2.9.5`) in the catalog supports type-safe `@Serializable` route objects. Use sealed-object routes under `ui/navigation/` rather than raw strings to keep the graph refactoring-safe.

## Files to Create

- `app/src/main/java/com/gshashank/btcagent/data/repository/AuthRepository.kt` — interface declaring `signInWithGoogle(activity: Activity): Result<FirebaseUser>`, `currentUser: FirebaseUser?`, `getIdToken(): Result<String>`, `signOut()`.
- `app/src/main/java/com/gshashank/btcagent/data/repository/AuthRepositoryImpl.kt` — `@Singleton` implementation injecting `FirebaseAuth`, `@IoDispatcher CoroutineDispatcher`. Uses `CredentialManager.create(context)` to request a `GetGoogleIdOption`, converts the resulting `GoogleIdTokenCredential` to a Firebase `AuthCredential`, calls `auth.signInWithCredential(...)` inside `withContext(ioDispatcher)`. Returns `Result.success(user)` or `Result.failure(exception)`. Handles `GetCredentialCancellationException` as a distinct user-cancel case mapped to a named exception type.
- `app/src/main/java/com/gshashank/btcagent/ui/auth/LoginUiState.kt` — sealed interface with four states: `Idle`, `Loading`, `Success(user: FirebaseUser)`, `Error(message: String)`.
- `app/src/main/java/com/gshashank/btcagent/ui/auth/LoginViewModel.kt` — `@HiltViewModel` injecting `AuthRepository`. Exposes `val uiState: StateFlow<LoginUiState>` initialised to `Idle`. `fun onGoogleSignIn(activity: Activity)` launches a coroutine in `viewModelScope`: emits `Loading`, calls `repository.signInWithGoogle(activity)`, emits `Success` or `Error`.
- `app/src/main/java/com/gshashank/btcagent/ui/auth/LoginScreen.kt` — contains two composables:
  - `LoginScreen(viewModel: LoginViewModel, onAuthenticated: () -> Unit)` — stateful wrapper. Collects `uiState` with `collectAsStateWithLifecycle`. Calls `onAuthenticated()` as a `LaunchedEffect` when state is `Success`. Passes state and `onGoogleSignIn` down to `LoginContent`.
  - `LoginContent(uiState: LoginUiState, onGoogleSignIn: () -> Unit)` — stateless. Renders the full layout described in the design spec (radial glow, logo tile, H1, supporting text, spacer, Google button, legal copy). Shows inline error text when state is `Error`. Renders `CircularProgressIndicator` inside the button and disables it when state is `Loading`. Uses only theme tokens from `BTCAgentTheme`. No `Scaffold`, no `TopAppBar`.
- `app/src/main/java/com/gshashank/btcagent/ui/navigation/AppRoutes.kt` — `@Serializable` sealed object hierarchy: `Route.Login` and `Route.Home`.
- `app/src/test/java/com/gshashank/btcagent/ui/auth/LoginViewModelTest.kt` — unit tests (see Test Cases).
- `app/src/test/java/com/gshashank/btcagent/data/repository/AuthRepositoryImplTest.kt` — unit tests (see Test Cases).
- `app/src/androidTest/java/com/gshashank/btcagent/ui/auth/LoginContentTest.kt` — Compose UI tests (see Test Cases).

## Files to Modify

- `gradle/libs.versions.toml` — add three new version entries and three new library entries:
  - Version: `credentials = "1.5.0"`, `googleid = "1.1.1"`, `uiTextGoogleFonts = "1.8.3"` (use the Compose BOM-aligned version for `ui-text-google-fonts`; prefer letting BOM manage it if the group is `androidx.compose.ui`).
  - Libraries: `androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }`, `androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }`, `googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }`, `androidx-compose-ui-text-google-fonts = { group = "androidx.compose.ui", name = "ui-text-google-fonts" }` (no version — governed by compose-bom).

- `app/build.gradle.kts` — two changes:
  1. Uncomment `alias(libs.plugins.google.services)` (line 10, the commented-out line; remove the comment block above it that explains the deferral).
  2. Add four `implementation(...)` entries in the dependencies block: `libs.androidx.credentials`, `libs.androidx.credentials.play.services.auth`, `libs.googleid`, `libs.androidx.compose.ui.text.google.fonts`.

- `app/src/main/java/com/gshashank/btcagent/ui/theme/Color.kt` — **full overwrite** (file is currently the purple template). Define all Bitcoin design tokens as named `Color` constants: `BtcAccent` (`#F7931A`), `BtcAccentDark` (darker amber, ~`#C8740D`, used for gradient end of logo tile), `BtcUp` (`#22C55E`), `BtcDown` (`#EF4444`), `BtcBg` (`#0E0E12`), `BtcBg2` (`#16161B`), `BtcCard` (slightly elevated surface, ~`#1C1C23`), `BtcCardBorder` (1px outline colour, ~`#2A2A35`), `BtcHairline` (subtle dividers, ~`#232328`), `BtcText` (`#FAFAFA`), `BtcText2` (`#FAFAFA` at 56% alpha), `BtcText3` (`#FAFAFA` at 40% alpha), `BtcChip` (subtle fill, ~`#FAFAFA` at 10% alpha or a flat dark value).

- `app/src/main/java/com/gshashank/btcagent/ui/theme/Theme.kt` — **full overwrite**. Replace the dynamic-purple template with a static dark-first Bitcoin `darkColorScheme`: `primary = BtcAccent`, `onPrimary = Color.Black`, `background = BtcBg`, `onBackground = BtcText`, `surface = BtcCard`, `onSurface = BtcText`, `surfaceVariant = BtcBg2`, `outline = BtcCardBorder`. Keep a `lightColorScheme` stub (can mirror dark values for now with a TODO comment). Set `dynamicColor = false` as default. Remove all `Build.VERSION` and `dynamicDarkColorScheme` / `dynamicLightColorScheme` imports/usage.

- `app/src/main/java/com/gshashank/btcagent/ui/theme/Type.kt` — **full overwrite**. Define two font families using `GoogleFont` provider from `androidx.compose.ui:ui-text-google-fonts`: `RobotoFlexFontFamily` (variable font, weights 500/600/700) and `RobotoMonoFontFamily` (weights 400/500, with `FontFeatureSettings("tnum")` for tabular numerics). Build a `Typography` object assigning `RobotoFlexFontFamily` to `displayLarge` through `labelSmall` (all UI text) and exposing `RobotoMonoFontFamily` as a top-level `val MonoTypography` for use in price displays. The `GoogleFont.Provider` must be declared once at top-level (not inside a composable) to avoid re-instantiation.

- `app/src/main/java/com/gshashank/btcagent/di/RepositoryModule.kt` — add a `@Binds @Singleton` abstract function that binds `AuthRepositoryImpl` to `AuthRepository`. The class already has the `abstract class` skeleton and `@Module @InstallIn(SingletonComponent::class)`.

- `app/src/main/java/com/gshashank/btcagent/MainActivity.kt` — replace the current `Greeting` placeholder with a `NavHost`. The `NavHost` starts at `Route.Login`. The Login composable calls `hiltViewModel<LoginViewModel>()`. On `onAuthenticated`, navigate to `Route.Home` with `popUpTo(Route.Login) { inclusive = true }` so back-press from Home does not return to Login. `Route.Home` renders a minimal placeholder `Text("Home — authenticated")` composable until the Home screen feature lands. Remove `Greeting` and its `@Preview`. Keep `enableEdgeToEdge()`. No `Scaffold` wrapping the `NavHost` (Login itself controls its own chrome-free layout; Home will add its own Scaffold in a later phase).

## Test Cases to Write

**`LoginViewModelTest` (unit, `app/src/test/`)**
- Use `FakeAuthRepository` implementing `AuthRepository` with a configurable `signInResult: Result<FirebaseUser>`.
- Use `UnconfinedTestDispatcher` + `runTest` from `kotlinx-coroutines-test`.
- Use Turbine's `test { }` extension on `uiState`.
- Scenarios:
  - `idle state on init`: initial emission is `LoginUiState.Idle`.
  - `loading then success on sign-in`: call `onGoogleSignIn(mockActivity)` with `FakeAuthRepository` returning `Result.success(mockUser)` — assert emissions are `[Idle, Loading, Success(mockUser)]` in order.
  - `loading then error on sign-in failure`: `FakeAuthRepository` returns `Result.failure(RuntimeException("network error"))` — assert emissions are `[Idle, Loading, Error("network error")]`.
  - `loading then error on user cancel`: `FakeAuthRepository` returns `Result.failure(UserCancelledException())` — assert emissions are `[Idle, Loading, Error]` with a user-friendly cancel message.
  - `success state does not re-emit on second call if already success`: calling `onGoogleSignIn` a second time while in `Success` state should not regress; assert new `Loading` cycle begins (no state machine lock-in).

**`AuthRepositoryImplTest` (unit, `app/src/test/`)**
- These tests require mocking `FirebaseAuth`, `CredentialManager`, and `Task<AuthResult>`. Use Mockito-Kotlin or manual fakes. Note: `CredentialManager` is an interface, so it can be faked directly.
- Scenarios:
  - `signInWithGoogle returns Success when Firebase succeeds`: mock `CredentialManager.getCredential(...)` to return a `GetCredentialResponse` containing a `GoogleIdTokenCredential`; mock `auth.signInWithCredential(...)` to return a completed `Task<AuthResult>` with a non-null `FirebaseUser`; assert `Result.isSuccess` and `Result.getOrNull()` is the user.
  - `signInWithGoogle returns Failure wrapping exception when Firebase fails`: mock Firebase task to fail with `FirebaseAuthException`; assert `Result.isFailure` and the cause is the `FirebaseAuthException`.
  - `signInWithGoogle returns Failure on credential cancellation`: mock `CredentialManager.getCredential` to throw `GetCredentialCancellationException`; assert `Result.isFailure` and wrapped exception is `UserCancelledException` (or the project's chosen cancel type).
  - `getIdToken returns token string on success`: mock `FirebaseUser.getIdToken(false)` task to return a `GetTokenResult` with a token string; assert `Result.isSuccess` wrapping that string.
  - `currentUser delegates to FirebaseAuth.currentUser`: assert that `repository.currentUser` returns `auth.currentUser`.

**`LoginContentTest` (instrumented Compose UI, `app/src/androidTest/`)**
- Use `createComposeRule()` with `BTCAgentTheme` wrapping `LoginContent`.
- Scenarios:
  - `renders all static elements in idle state`: assert logo tile visible (by content description `"Bitcoin logo"`), title `"BTC AI Agent"` visible, supporting paragraph text visible, Google sign-in button with label `"Continue with Google"` visible, legal fine-print visible.
  - `clicking sign-in button invokes onGoogleSignIn callback`: set `uiState = Idle`, click the button, assert the lambda was called exactly once.
  - `loading state disables button and shows spinner`: set `uiState = Loading`, assert button is not enabled, assert `CircularProgressIndicator` semantic is present (tag or content description), assert "G" chip is not visible (or spinner replaces it).
  - `error state shows error message`: set `uiState = Error("Sign-in failed")`, assert text `"Sign-in failed"` is displayed on screen.
  - `error state button remains clickable`: set `uiState = Error(...)`, assert button is enabled (user can retry).
  - `legal text is not clickable / decorative`: assert fine-print text node exists and is not clickable.

## Risks / Assumptions

- **Web Client ID requirement**: Credential Manager's `GetGoogleIdOption` requires a `serverClientId` string (the OAuth 2.0 Web Client ID from the Firebase project's SHA-1 fingerprint). This value must be placed in `res/values/strings.xml` as `default_web_client_id`. The `google-services` plugin auto-generates this string resource from `google-services.json` when applied — verify the JSON contains the correct `oauth_client` entry of type `3` (web client). If missing, sign-in will silently fail with a credential exception.
- **SHA-1 fingerprint registration**: The debug keystore SHA-1 must be registered in the Firebase Console under the Android app before Google sign-in will work on a physical device or emulator with Play Services. This is a Firebase Console configuration step, not a code step.
- **`hilt-android-testing` not yet in catalog**: The `LoginViewModelTest` is a pure JVM unit test and does not need Hilt testing infrastructure. However, if future tests need `@HiltAndroidTest`, `hilt-android-testing` and `hilt-android-compiler` (for `kspAndroidTest`) must be added to the catalog. Out of scope for this plan but flagged for later.
- **Mockito-Kotlin not in catalog**: `AuthRepositoryImplTest` mocks `FirebaseAuth` and `CredentialManager`. `mockito-kotlin` (`org.mockito.kotlin:mockito-kotlin`) is not currently in `libs.versions.toml`. The implementer must add it (current stable: `5.4.0`) or use manual fakes for all collaborators. Plan assumes `mockito-kotlin` will be added as a `testImplementation` dep.
- **`collectAsStateWithLifecycle` requires `lifecycle-runtime-compose`**: The `LoginScreen` stateful wrapper should use `collectAsStateWithLifecycle` (not `collectAsState`) for proper lifecycle awareness. The `lifecycle-runtime-ktx` artifact is already present but `lifecycle-runtime-compose` (which provides the Compose extension) may need to be explicitly added. Check the current `composeBom` (`2026.02.01`) — if it includes `lifecycle-runtime-compose`, no explicit version entry is needed; just add `implementation(libs.androidx.lifecycle.runtime.compose)` and a library entry without a version ref.
- **`FilterExceptions` in `CredentialManager` flow**: Some `GetCredentialException` subtypes (e.g., `NoCredentialException`) occur when no Google account is available on the device. `AuthRepositoryImpl` must handle this as a distinct failure path separate from cancellation.
- **Navigation back-stack on re-authentication**: The `popUpTo(Route.Login) { inclusive = true }` in `MainActivity` means process death + return will re-show Login. This is correct behaviour for Phase 1 but the implementer should be aware that persistent auth state (checking `FirebaseAuth.currentUser` on cold start to skip Login) is deferred to a later phase.
- **`CredentialManager` is not available below API 34 via the Jetpack backport**: The `credentials` artifact provides a compatibility shim down to API 16 (via `credentials-play-services-auth`) but behaviour differs. Test on API 28 and API 34 emulators. The `minSdk = 24` in `defaultConfig` is safe.
- **Font provider availability in tests**: `GoogleFont.Provider` makes a network/Play Services call. In Compose UI tests running on the emulator this is fine; in pure unit tests `RobotoFlexFontFamily` and `RobotoMonoFontFamily` are never instantiated, so no issue. Do not call font provider code in JVM unit tests.
- **`compileSdk` uses `release(37)` DSL**: This is AGP 9.x syntax (confirmed by `agp = "9.2.1"`). No change needed; noting it because the Credential Manager and GoogleId artifacts must be compatible with SDK 37 / AGP 9.2.1.

✅ Plan ready. Review PLAN.md and run /project:implement to proceed.
