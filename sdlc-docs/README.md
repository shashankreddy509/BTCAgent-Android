# BTC Trading — Native Android App Feasibility

Feasibility study for a **native Android app** that mirrors the existing **BTC AI Agent** web dashboard.

> **TL;DR — Highly feasible.** Backend already exposes a clean REST + WebSocket API behind Firebase Auth. The Android app is a *thin native client* over that API. No backend rewrite. Main effort = rebuilding the SPA's ~16 pages as Compose screens + wiring Firebase Google sign-in to mint the bearer token the API already expects. Main bottlenecks: live-trading risk on mobile, background execution limits, and a few server-rendered/OAuth-redirect flows that don't map cleanly to native.

---

## 1. What the BTC bot actually does (source of truth)

Repo: `/Users/shashankreddyganta/Documents/btc-ai-agent`
Stack: **Python 3.12 · FastAPI · Firebase/Firestore · Anthropic Claude · ccxt brokers**
Deploy: EC2 `ap-south-1`, Nginx → port 8000, domain `btc.gshashank.com`
Auth: **Firebase Auth (Google sign-in)** → bearer ID token verified server-side (`verify_token`). Owner/admin gate by email + an approved-emails allowlist.

### Core capabilities (from web dashboard — 16 pages)
| Page | What it does |
|------|--------------|
| Dashboard | Live price (WS), open positions, P&L |
| Trading | Start/stop scanner, autostart, live/paper, position cards, cancel/edit |
| Scanner | Candlestick pattern hits (4-Flag, Morning/Evening Star, Engulfing) across TFs |
| Manual | Discretionary entries, manual levels, manual limit orders, pending cancel |
| Liquidity | CoinGlass liquidation-cluster heatmap snapshots |
| OI | Native Open Interest aggregator (USDT-perp + coin-M), Pine-style signals |
| Regime | Daily Markov BTC regime (Bull/Bear/Sideways) |
| Markov | Multi-ticker Markov transition matrices + custom tickers |
| VP | Session/Volume Profile, naked POCs |
| Vishal | Vishal-Sir strategy detections (6 GTI-zone strategies) |
| Analysis | Trade analytics — buckets, expectancy, win-rate, drawdown rankings |
| Reports | Per-user signals + positions + closed-trade history |
| Briefing | AI morning briefing (RSS + Reddit → Claude summary) |
| Settings | App + user config (API keys, scanner/trading params) |
| Users | Admin: approve emails, set mode, stop user |
| Guide / Support | Static help |

### Brokers wired (live execution)
`coinbase · binance · bybit · coindcx · delta · pepperstone` (Pepperstone via OAuth redirect).

---

## 2. API surface (what the Android app calls)

Base: `https://btc.gshashank.com` · all `/api/*` require `Authorization: Bearer <firebase_id_token>`.

**Public-ish (allowed-email gate)** `/api/`
- `GET price · liquidity · scan · brief · regime-log · status · support · access/check`
- `GET markov/tickers · markov/tickers/{ticker}/history`
- `POST scan/trigger · brief/trigger · oi/ingest`

**Trading** `/api/trading/`
- `GET state · reports · manual-levels · oi/status · oi/native`
- `POST start · autostart · stop · test-order · test-order/close · manual-entry · manual-limit`
- `POST position/{id}/cancel · position/{id}/edit · manual-pending/{id}/cancel`

**Settings** `/api/settings/` (some admin-only)
- `GET app · user` · `PUT app · user`
- `POST markov/custom-ticker · pepperstone/auth-url` · `DELETE markov/custom-ticker/{ticker}`

**Realtime**
- `WS /ws/price` — live BTC price push (use OkHttp/Ktor WebSocket).

**Server-rendered / OAuth (do NOT map to native screens)**
- `GET / · /login · /health`
- `GET /auth/pepperstone · /auth/pepperstone/callback` — OAuth redirect flow → open in Custom Tab.

---

## 3. Android architecture (recommended)

- **Language/UI**: Kotlin + Jetpack Compose + Material 3
- **Pattern**: MVVM, `StateFlow`, unidirectional data flow
- **Network**: Retrofit + OkHttp (REST) · OkHttp WebSocket (live price) · `kotlinx.serialization`
- **Auth**: Firebase Auth Android SDK (Google sign-in) → `getIdToken()` → OkHttp `Authorization` interceptor; refresh on 401
- **Charts**: MPAndroidChart or Vico (Compose-native) for price / VP / OI / heatmap
- **Push**: FCM for trade-event alerts (replaces Telegram path on mobile)
- **Local**: DataStore for prefs, Room optional for trade history cache
- **Min SDK**: 26+

No backend changes required for read + most write flows — the same bearer token the web SPA mints works.

---

## 4. Feasibility per feature

| Feature | Feasible? | Notes |
|---|---|---|
| Google sign-in / token | ✅ Easy | Firebase Android SDK = same identity provider as web |
| Live price (WS) | ✅ Easy | OkHttp WebSocket → Compose state |
| Dashboard / positions / reports | ✅ Easy | Pure REST GET → list/cards |
| Scanner / patterns / OI / regime / Markov / VP / liquidity | ✅ Easy–Med | REST GET; charts need a chart lib |
| Settings (app/user) | ✅ Med | Forms; respect masked-field (`****`) rules from backend |
| Start/stop scanner, autostart | ✅ Med | REST POST; surface live state via polling or WS |
| Manual entry / limit / edit / cancel | ✅ Med | POST; needs careful confirm UX (real money) |
| Live order execution (test-order etc.) | ⚠️ Med-High | Works, but irreversible — must gate with biometric + confirm |
| Trade-event alerts | ✅ Med | Add FCM; backend currently pushes Telegram — needs a device-token registration endpoint |
| AI briefing | ✅ Easy | GET pre-rendered text |
| Admin (users, approve emails, set mode) | ✅ Med | Admin-gated REST; build behind admin check |
| Pepperstone OAuth | ⚠️ Med | Redirect flow → Chrome Custom Tab + deep-link callback |
| Background scanner running | ❌ N/A on device | Scanner runs server-side on EC2 — app just observes. Don't try to run the loop on-device |

---

## 5. Bottlenecks / risks

1. **Live-money execution on a phone** — biggest risk. Fat-finger orders, no confirmation, lost network mid-order. Mitigate: biometric gate, explicit confirm dialog, default to paper mode, server-side idempotency on order endpoints.
2. **FCM push not built yet** — backend alerts go to Telegram/email. Need: device-token register endpoint + FCM send path server-side. Small backend addition.
3. **Background limits** — Android kills background sockets. Live price WS only works foreground; rely on FCM for alerts when backgrounded, not a persistent socket.
4. **OAuth redirect (Pepperstone)** — `/auth/pepperstone/callback` is web-redirect based. Need a deep-link / Custom Tab bridge + possibly a mobile callback URL whitelisted server-side.
5. **Masked secrets** — GET settings returns `****` for API keys; PUT skips masked values. App must not show or resend masked values as real.
6. **Allowed-email gate** — non-approved users are blocked. App must handle the `access/check` 403 path with a clean "pending approval" screen.
7. **Auth token refresh** — Firebase ID tokens expire ~1h. Interceptor must refresh on 401 and retry.
8. **Charts parity** — heatmap / volume-profile / OI visuals are non-trivial to reproduce natively; budget time or ship simplified versions first.
9. **API has no versioning / mobile contract** — currently shaped for the SPA. Recommend a thin `/api/mobile/*` or version header later if web and app diverge.

---

## 6. Suggested phasing

- **Phase 0** — Auth + allowed-email gate + live price (proves the whole pipe).
- **Phase 1 (read-only)** — Dashboard, positions, reports, scanner, briefing, regime/OI/Markov views.
- **Phase 2 (control)** — Start/stop scanner, settings, manual entries (paper-first), confirm UX.
- **Phase 3 (live trading)** — Live execution with biometric gate + FCM alerts (needs small backend add for device tokens).
- **Phase 4** — Admin panel, Pepperstone OAuth, advanced charts.

---

## 7. Backend additions needed (small)

- FCM device-token register endpoint + send path (for mobile alerts).
- Mobile-friendly OAuth callback (deep link) for Pepperstone.
- (Optional) order idempotency keys to make mobile retries safe.

Everything else is reachable today through the existing API.

*Open `feasibility.html` in a browser for the visual version.*
