# BTC Agent — Android

[![Android CI](https://github.com/shashankreddy509/BTCAgent-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/shashankreddy509/BTCAgent-Android/actions/workflows/android-ci.yml)

A native Android companion app for a self-hosted **BTC AI trading agent**. The agent runs server-side: it scans the
market for signals and places trades through a broker. This app is the mobile control panel for it. You can watch the
live BTC price and open positions, start or stop the scanner, switch between paper and live execution, place manual
orders, and get push alerts when the agent fires a signal or fills an order.

Why a native app instead of the web dashboard: live trading needs quick, secure control from a phone. Real-money
actions are gated (a LIVE-mode confirmation dialog, and a biometric prompt before a live manual order). Alerts arrive
as FCM push notifications. The UI is Material 3 Compose with a type-safe navigation graph and offline, loading and
error states.

**At a glance:** Kotlin · Jetpack Compose · MVVM + unidirectional data flow · Hilt · Retrofit/OkHttp + WebSocket ·
Firebase Auth + FCM · runtime feature flags · 220 Kotlin source files · **670 JVM unit tests + 61 instrumented Compose UI tests**

---

## Screenshots

> _Placeholder: screenshots to be added._

| Dashboard | Markets | Trading Control | Manual Entry | Settings |
|---|---|---|---|---|
| _todo_ | _todo_ | _todo_ | _todo_ | _todo_ |

---

## Features

| Area | What it does |
|---|---|
| **Sign-in & access gate** | Google sign-in via Credential Manager exchanged for a Firebase session. The backend allow-list is checked afterwards, with an "approval pending" state for users not yet approved. |
| **Dashboard** | Live BTC price streamed over a WebSocket, combined with REST account state and a connectivity banner. |
| **Positions** | List and detail views, close a position, edit TP / SL. |
| **Trade** | Start or stop the scanner. Switch PAPER / LIVE (LIVE needs an explicit real-funds confirmation). Toggle autostart and alerts. |
| **Manual entry** | MARKET or LIMIT orders, long or short, with an order summary. LIVE orders need `BiometricPrompt` (strong biometric or device credential) before they are sent. |
| **Markets** | Open Interest, BTC regime, Markov transition matrix, volume profile, liquidity map, trade analytics, and an AI Morning Briefing rendered as Markdown. Signal scanner with filters. |
| **Reports** | Signals today, win rate, weekly P&L, recent closed trades. |
| **Settings** | Appearance (color theme, dark mode, dashboard layout), trading parameters, broker API credentials, sign out. |
| **Admin** | Approve or reject users, set a user's mode, stop a user's scanner. |
| **Push alerts** | FCM token registered with the backend on sign-in or token refresh, and unregistered on sign-out. The notification channel covers signals, fills and alerts. |

---

## Stack

| Concern | Library |
|---|---|
| UI | Jetpack Compose (BOM), Material 3, material-icons-extended, Google Fonts |
| Navigation | navigation-compose with type-safe `@Serializable` routes and nested per-tab graphs |
| DI | Hilt (KSP) and hilt-navigation-compose |
| Networking | Retrofit 3, OkHttp 5 (REST and WebSocket), kotlinx.serialization |
| Async | Kotlin Coroutines, `StateFlow` / `callbackFlow` |
| Persistence | DataStore Preferences |
| Auth & push | Firebase Auth, Firebase Cloud Messaging, AndroidX Credential Manager + Google Identity |
| Security | AndroidX Biometric |
| Charts / content | Vico (Compose M3 charts), multiplatform-markdown-renderer |
| Testing | JUnit 4, kotlinx-coroutines-test, Turbine, OkHttp MockWebServer, mockito-kotlin, Compose UI test |

The versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Background work uses
coroutines; WorkManager is not used.

---

## Architecture

It is a single-module app (`:app`) with its package root at `com.gshashank.btcagent`, layered as follows:

```
app/src/main/java/com/gshashank/btcagent/
├── BtcApplication.kt        @HiltAndroidApp — kicks off the first feature-flag fetch
├── MainActivity.kt          Single activity; applies theme, hosts the top-level NavHost
├── ui/                      Compose screens + @HiltViewModel per feature
│   ├── navigation/          Type-safe routes: Onboarding → Login → Gate → Home
│   ├── shell/               AppShell: Scaffold + 5-tab bottom bar (Home, Markets, Trade, Reports, Settings)
│   ├── onboarding/ auth/ gate/ home/ positions/ trade/ markets/ scanner/ reports/ settings/ admin/
│   ├── components/state/    UiState<T> + shared Loading / Empty / Error / Offline scaffolding
│   └── theme/
├── data/
│   ├── network/             18 Retrofit *Api interfaces + DTOs, AuthInterceptor, TokenAuthenticator,
│   │                        PriceWebSocketClient
│   ├── repository/          Repository interfaces + *Impl, sealed per-feature *Result types, CatalogFlags
│   └── model/               Domain models and pure calculators (e.g. order summary, trade aggregation)
├── di/                      Hilt modules: Network, Repository, Firebase, DataStore, Dispatchers
└── core/                    BtcMessagingService (FCM), NetworkMonitor, biometric/
```

### Data flow

```
Retrofit API (suspend, Response<Dto>)        OkHttp WebSocket (callbackFlow)
            │                                            │
            ▼                                            ▼
RepositoryImpl — withContext(IO), DTO → domain model, returns a sealed *Result (never throws)
            │
            ▼
@HiltViewModel — exposes StateFlow<UiState<T>>  (Loading · Empty · Error · Offline · Ready)
            │
            ▼
Compose screen — collectAsStateWithLifecycle()
```

- **Feature stack pattern.** Each feature has the same five parts: `XxxApi` (Retrofit), `XxxDto`, `XxxRepository`
  (interface), `XxxRepositoryImpl`, and a sealed `XxxResult`. They are bound in `di/RepositoryModule.kt` and
  `di/NetworkModule.kt`. Repositories map failures to result types instead of throwing.
- **Auth on every request.** `AuthInterceptor` attaches the Firebase ID token as a bearer header. On a 401,
  `TokenAuthenticator` force-refreshes the token and retries once. A separate unauthenticated OkHttp client serves
  the public endpoints. HTTP logging is headers-only in debug with `Authorization` redacted, and off in release.
- **Live price.** `PriceWebSocketClient` is a cold `callbackFlow` over an OkHttp WebSocket. It reconnects with
  exponential backoff (1 s up to 30 s), runs a staleness watchdog, and rejects non-finite or non-positive prices.
- **Runtime feature flags.** Features can be switched on or off server-side through "catalog" flags (7 are defined
  in `data/repository/CatalogFlags.kt`, gating the Markets tiles, Manual Entry, and login and access-check behavior).
  Flags are fetched at startup, polled every 10 minutes, and cached in DataStore as a last-known-good map. An absent
  flag means OFF. A security-sensitive flag can default to the safe path instead, so a failed fetch never opens it.
- **Environments.** There are two product flavors, `dev` and `prod`, which install side by side (`.dev`
  applicationIdSuffix). Each flavor injects its backend base URL and WebSocket URL through `BuildConfig`.

---

## Testing

There are **59 files in `app/src/test`** (57 test classes and 2 helpers) with **670 `@Test` methods**, plus
**8 files in `app/src/androidTest`** with **61 `@Test` methods**.

| Group | Files | What it covers | Tools |
|---|---|---|---|
| Repository (network) | 19 | Each `*RepositoryImpl` against a real HTTP server: request paths and bodies, DTO → model mapping, and the error-code → sealed-result mapping | MockWebServer, coroutines-test |
| Repository (other) | 4 | Auth (Credential Manager → Firebase), appearance prefs, trade aggregation, volume-profile mapping | mockito-kotlin, coroutines-test |
| Network | 2 | `AuthInterceptor` header injection. `PriceWebSocketClient` parsing, reconnect and staleness. | MockWebServer, Turbine |
| ViewModel | 21 | State transitions for every major screen (Dashboard, Positions, Trading Control, Manual Entry, Markets, Settings, Admin, and so on) | Turbine, coroutines-test, mockito-kotlin (13 of them) |
| Model / parsing | 5 | Parsing and validation for OI signals, regime, Markov, liquidity and volume-profile data | JUnit |
| UI logic | 6 | `UiState`, theme, order summary math, time and timeframe formatters | JUnit |
| Compose UI (instrumented) | 8 | Login, Gate, Dashboard, Positions list, Reports, the shared state scaffold, and the app shell's tab navigation | Compose `createComposeRule` |

Commands:

```bash
./gradlew testDevDebugUnitTest testProdDebugUnitTest   # JVM unit tests, both flavors
./gradlew connectedDevDebugAndroidTest                 # instrumented Compose tests — needs a device/emulator
```

CI ([`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)) runs `assembleDebug` plus the debug unit tests for both flavors
on every push and every pull request to `main`.

---

## Build

**Requirements**

- Android Studio (AGP 9.2) with the Android SDK, platform 37.
- JDK 21 to run Gradle (`gradle/gradle-daemon-jvm.properties`) and JDK 17 for Kotlin compilation (`jvmToolchain(17)`).
  If neither JDK is installed, Gradle can provision them through the foojay resolver.
- minSdk 24, targetSdk 36.

**Firebase config (required).** `google-services.json` is gitignored and never committed. Create a Firebase project
with Google sign-in enabled. Register Android apps for `com.gshashank.btcagent` and `com.gshashank.btcagent.dev`,
then place the downloaded file at `app/google-services.json` (or per flavor under `app/src/<flavor>/`). Google
sign-in reads the web OAuth client from that file (`default_web_client_id`).

```bash
./gradlew assembleDebug                 # builds devDebug + prodDebug
./gradlew installDevDebug               # install the dev flavor on a connected device
```

The app is a client only. Sign-in, the access gate and all data require a running instance of the BTC AI Agent
backend (a separate repository). Point each flavor at your own backend through the `BASE_URL` / `WS_URL` fields in
`app/build.gradle.kts`.

To build without real Firebase credentials, for example in CI, a placeholder `google-services.json` is enough to
compile and run the unit tests. See the workflow for the one it generates.
