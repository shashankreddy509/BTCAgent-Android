# BTC AI Agent — Android App UI Design Brief

A brief for designing the UI of a native Android app. Hand this to a design tool to generate screen mockups. Everything needed to design is here; no codebase access required.

---

## 1. What this app is

A **native Android client** for an existing crypto-trading bot ("BTC AI Agent"). The bot already runs server-side (scans markets, executes trades, sends alerts) and has a web dashboard. This app is a **thin mobile client** over that same backend — it lets the user monitor the bot, view analytics, control the scanner, place manual trades, and (carefully) execute live orders from their phone.

The bot itself stays on the server. The app **observes and controls** it; it does not run trading logic on-device.

**Primary user:** the bot owner / approved traders. Single-user-feeling, data-dense, finance-grade. Not a consumer mass-market app.

---

## 2. Design direction

- **Platform:** Android, Jetpack Compose, **Material 3**. Follow M3 components, dynamic color optional.
- **Theme:** Dark-mode first (traders live in dark dashboards at night). Support light too.
- **Feel:** Professional fintech / trading terminal. Dense but legible. Think Coinbase Pro / TradingView / Binance app energy — data-forward, fast-scanning, calm under heavy numbers.
- **Color semantics:** green = up/profit/long, red = down/loss/short. A clear accent for primary actions. Muted neutrals for chrome so the data pops.
- **Typography:** tabular/monospaced figures for prices and P&L (numbers must align in columns). Strong numeric hierarchy.
- **Money safety is a visual theme:** live-trade actions must look distinct and "heavier" than read-only ones — confirmation dialogs, a biometric gate, paper-vs-live mode badges everywhere.
- **States:** every data screen needs loading (skeleton), empty, and error variants. Offline is common on mobile.

---

## 3. Navigation shell

Bottom navigation bar with a few top-level destinations; secondary screens pushed on top. Suggested grouping:

- **Dashboard** (home)
- **Markets** (hub → analytics screens)
- **Trading** (control + positions)
- **Reports**
- **Settings** (+ profile/admin)

Plus an auth/gate flow that sits *outside* the shell (Login, Pending-approval).

---

## 4. Screens to design

Grouped by build phase. ~19 screens total.

### A. Auth & gate (outside bottom nav)
1. **Login** — Google sign-in (single primary button), app logo, tagline. Minimal.
2. **Pending approval** — shown when a signed-in user's email isn't yet allow-listed. Friendly "waiting for owner approval" state, sign-out option.

### B. Phase 1 — Read-only (monitoring)
3. **Dashboard** — the home. Live BTC price (updates in real time, ticking), today's P&L, open-positions summary, bot status (running/stopped, live/paper). The "everything at a glance" screen.
4. **Positions** — list/cards of open trades: symbol, side (long/short), entry, current, P&L %, size. Tap → detail.
5. **Reports** — per-user history: signals fired, open positions, closed-trade log with outcomes.
6. **Scanner** — candlestick pattern hits (4-Flag, Morning/Evening Star, Engulfing) across timeframes. List of detections with timeframe + symbol + pattern.
7. **Briefing** — AI-generated morning market briefing (a block of readable summary text). Read-only.

### C. Markets hub — analytics (read-only, chart-heavy)
8. **Markets hub** — entry tiles linking to the analytics screens below.
9. **Open Interest (OI)** — OI aggregator with Pine-style signals. Chart + signal readout.
10. **Regime** — daily Markov BTC regime: Bull / Bear / Sideways. A clear current-state indicator + history.
11. **Markov** — multi-ticker Markov transition matrices (a grid/matrix viz) + ability to add custom tickers.
12. **Volume Profile (VP)** — session/volume profile, naked POCs. Horizontal histogram against price.
13. **Liquidity** — liquidation-cluster heatmap snapshots (heatmap viz).
14. **Vishal strategy** — detections from 6 named zone-based strategies. List/grid of hits.
15. **Analysis** — trade analytics: win-rate, expectancy, drawdown rankings, buckets. Dashboard of stat cards + small charts.

### D. Phase 2 — Control
16. **Trading control** — start/stop the scanner, toggle autostart, switch live/paper mode. Big clear state, guarded toggles. Position cards with cancel/edit.
17. **Manual entry** — discretionary trade entry form: symbol, side, size, price/levels. Manual limit orders, pending-order cancel. **Heavy confirm UX** (real money).
18. **Settings** — app + user config: API keys (broker), scanner params, trading params. Forms. **Masked-secret rule:** existing keys show as `••••`; user can overwrite but the app never displays the real stored value.

### E. Phase 3/4 — Live & admin
19. **Users (admin only)** — approve pending emails, set a user's mode, stop a user. Admin-gated list with actions.
- **Guide / Support** — static help content (low priority).
- **Live-execution confirm** — a modal/sheet pattern (not a full screen): biometric prompt + explicit confirm before any live order. Design this as a reusable high-friction confirmation component.

---

## 5. Cross-cutting UI components needed

- **Live price ticker** — a value that updates in real time, flashes green/red on tick direction. Used on Dashboard and Trading.
- **Position card** — symbol, side badge, entry/current, P&L (color-coded), size, actions.
- **Mode badge** — "PAPER" vs "LIVE" indicator, visible wherever trading actions exist. LIVE should feel alarming/serious.
- **Confirm dialog / biometric sheet** — reusable high-friction gate for money actions.
- **Stat card** — labeled metric with value + optional delta + tiny sparkline. Used across analytics.
- **Chart containers** — line (price/OI), matrix/grid (Markov), histogram (VP), heatmap (liquidity).
- **Empty / loading (skeleton) / error / offline** states for every data surface.

---

## 6. Key UX rules (please honor in mockups)

1. **Real money = high friction.** Any live order needs an explicit confirm + biometric step, and defaults toward paper mode. Make destructive/irreversible actions visually distinct.
2. **Numbers align.** Use tabular figures; P&L and prices in columns.
3. **Color = direction.** Green up/long/profit, red down/short/loss, consistently.
4. **Dense but scannable.** This is a trader tool; prioritize information density over whitespace, but keep clear hierarchy.
5. **Masked secrets.** Never show stored API keys; show `••••` with an overwrite affordance.
6. **Every screen has non-happy states.** Loading, empty, error, offline.

---

## 7. What NOT to design

- No on-device trading engine UI — the bot runs server-side; the app only observes/controls.
- No Pepperstone OAuth screens (handled via an external browser tab, not a native screen).
- No server-rendered/web pages.

---

## 8. One-line summary to lead with

> A dark, data-dense Material 3 Android trading-terminal app that lets an approved user monitor a server-side BTC trading bot's live price, positions, and analytics, control its scanner, and place manual or live trades behind strong money-safety confirmation gates.
