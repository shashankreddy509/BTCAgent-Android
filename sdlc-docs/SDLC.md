# BTC Trading Android App — SDLC Document

Full software-development-lifecycle breakdown for the native Android app that mirrors the
**BTC AI Agent** web dashboard. Each phase has **multiple sub-steps** — none is a single action.

> **Where we are:** Phases 1–3 (Requirements, Analysis, Design) ✅ complete and approved.
> **Next:** Phase 4 Development → starting with **Phase 0 (Foundation)**.

Project: `/Users/shashankreddyganta/AndroidStudioProjects/BTCAgent` · Package `com.gshashank.btcagent`
Backend (source of truth): `/Users/shashankreddyganta/Documents/btc-ai-agent` (FastAPI · `btc.gshashank.com`)

---

## Phase overview

| # | Phase | Status | Sub-steps |
|---|-------|--------|-----------|
| 1 | Requirements | ✅ Done | 5 |
| 2 | Analysis | ✅ Done | 7 |
| 3 | Design | ✅ Done (approved) | 10 |
| 4 | Development | ⬜ **Next** | Phase 0 + 9-step loop/screen |
| 5 | Testing | ⬜ Continuous | 7 |
| 6 | Deployment | ⬜ Later | 8 |
| 7 | Maintenance | ⬜ Ongoing | 5 |

---

## 1. Requirements ✅

What the app must do, derived from the existing bot.

- **1a** Identify source of truth — the `btc-ai-agent` FastAPI backend.
- **1b** Inventory bot features — 16 dashboard pages (dashboard, trading, scanner, manual, liquidity, OI, regime, markov, VP, vishal, analysis, reports, briefing, settings, users, guide).
- **1c** Define app goal — **thin native client** over the existing API; scanner stays server-side; **no backend rewrite**.
- **1d** Feasibility verdict + bottlenecks — live-money risk, FCM not built, background limits, OAuth redirect, masked secrets, token refresh.
- **1e** Deliverables — `README.md` + `feasibility.html`.

## 2. Analysis ✅

Pin down the exact contract the app codes against.

- **2a** Map API surface — ~50 REST endpoints + 1 WebSocket.
- **2b** Auth model — Firebase ID token `Authorization: Bearer`; `401` invalid, `403` not-allowed/admin, `429` rate-limit; allowed-email gate.
- **2c** Request/response shapes — per key endpoint (state, start/stop, manual-entry, reports, settings).
- **2d** Masked-secret rules — GET returns `****`; PUT skips `****`; never display/resend masked as real.
- **2e** WS price format — `{price: float}`, unauthenticated.
- **2f** Backend gaps — FCM push (none today, Telegram only) + mobile Pepperstone OAuth callback.
- **2g** Non-functional needs — offline handling, token refresh, biometric on live trades, foreground-only sockets.

## 3. Design ✅ (approved)

How it's built.

- **3a** Stack + DI — Kotlin/Compose/M3/MVVM + **Hilt** (rationale vs Koin: compile-time graph, Jetpack integration).
- **3b** Module/package layout — single `:app` module, package-by-feature.
- **3c** Hilt DI graph — 4 modules (Network, Firebase, Repository, Dispatcher), scopes, `@Binds`/`@Provides`.
- **3d** Auth interceptor + 401 authenticator — Bearer attach + forced-refresh-retry-once.
- **3e** Repository layer — 7 repos (Auth, Access, Trading, Settings, MarketData, Price, Device).
- **3f** WS as lifecycle-scoped `Flow<Double>` — reconnect backoff, foreground-only.
- **3g** Screen designs — 19 screens + bottom-nav shell + Gate routing.
- **3h** UX rules — confirm dialogs, biometric on live, loading/empty/error states.
- **3i** Test strategy — unit/interceptor/Hilt-instrumented/Compose.
- **3j** SDLC roadmap — this document.

## 4. Development ⬜ (NEXT)

Build it. Two parts: a one-time **foundation**, then a **repeatable loop per screen**.

### 4a — Phase 0: Foundation (no TDD loop, just wiring that compiles)
1. Build wiring: Java 11→17; add Hilt/KSP/Retrofit/OkHttp/serialization/Firebase/nav/Vico to version catalog + `build.gradle.kts`.
2. Add `INTERNET` permission + `@HiltAndroidApp BtcApplication` + manifest `android:name`.
3. Create empty `di/ core/ data/ ui/` package skeleton + the 4 Hilt modules (compile, no logic).
4. Drop in `google-services.json` (user-supplied from Firebase console).
5. Verify `./gradlew :app:assembleDebug` green — **the DI graph compiles**.
   - *Exit criteria:* app builds with full DI graph + can mint a Firebase token.

### 4b–4n — Per-screen development loop (the 9 inner steps)
Repeated for each screen. Maps to agent pipeline `planner → test-writer → implementer → test-runner → code-review-coordinator → changelog`.

1. **Branch** — feature branch off main.
2. **Contract first** — `*Dto` (`@Serializable`) + Retrofit API method + repository method signature.
3. **Write failing tests (TDD)** — ViewModel + repo behavior tests that compile but fail.
4. **Implement** — repo impl → `@HiltViewModel` ViewModel → Compose `Screen` (stateless `Content` + state holder).
5. **Wire DI** — add Hilt `@Binds`/`@Provides`, inject repo into ViewModel.
6. **Run tests → green** — fix loop, max retries.
7. **Manual verify** — run on emulator/device, confirm the real screen behaves.
8. **Review** — security + quality + memory review.
9. **Commit + changelog** — conventional commit.

### Screen build order
Login → Gate/Pending → **Dashboard** (proves WS + auth) → Reports/Scanner/Briefing (read-only) →
Markets hub + analytics (OI/Regime/Markov/VP/Liquidity/Vishal/Analysis) → Trading control → Manual → Settings → Users (admin) → Guide.

### Dev phasing (groups of screens)
- **Phase 1** — Read-only: dashboard, positions, reports, scanner, briefing, analytics views.
- **Phase 2** — Control: start/stop/autostart, settings (masking), manual entries (paper-first).
- **Phase 3** — Live trading: execution + biometric gate + FCM alerts (needs backend FCM add).
- **Phase 4** — Admin panel, Pepperstone OAuth deep link, advanced charts.

## 5. Testing ⬜ (continuous — runs alongside Development, not a tail phase)

- **5a** Unit — ViewModel + repo via fakes (`Turbine`, `TestDispatcher`).
- **5b** Interceptor/Authenticator vs `MockWebServer` — Bearer attached; 401 → exactly one refresh-retry; second 401 gives up. **Highest-value test.**
- **5c** Hilt instrumented — `@HiltAndroidTest`, `@TestInstallIn` (swap NetworkModule→MockWebServer, fake Firebase), `@BindValue`.
- **5d** Compose UI tests — screen state rendering + interactions.
- **5e** WS reconnect test — `withWebSocketUpgrade`, assert emits + backoff reconnect.
- **5f** Manual device smoke — sign-in → token → price ticks → bg/fg re-establish → expired-token refresh path.
- **5g** Regression run before each merge.

## 6. Deployment ⬜

- **6a** Ship backend adds — FCM register endpoint + send hook in `scanner.py _notify_trade`; mobile OAuth callback.
- **6b** App signing config + keystore.
- **6c** `versionCode`/`versionName` bump.
- **6d** Build signed release (R8 minify).
- **6e** Internal/closed test track (Play Console).
- **6f** Firebase prod config — `google-services.json`, SHA-1/256 fingerprints registered.
- **6g** Staged rollout.
- **6h** Crash/ANR monitoring (Crashlytics).

## 7. Maintenance ⬜ (ongoing)

- **7a** Monitor crashes / API errors.
- **7b** Handle backend API drift — DTO `ignoreUnknownKeys=true` buffers minor field additions.
- **7c** Mirror new bot features into the app.
- **7d** Dependency + security updates.
- **7e** Capture lessons (`tasks/lesson.md`).

---

*Visual version: `sdlc.html` (open in browser). Architecture detail: plan file + `README.md` / `feasibility.html`.*
