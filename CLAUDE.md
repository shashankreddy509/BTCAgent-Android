# BTCAgent — project instructions

Native Android client (Kotlin, Jetpack Compose, Hilt) for the BTC AI Agent. Backend lives in a separate Python repo at `~/Documents/btc-ai-agent` — verify API contracts there, never assume.

## Jira
Jira: cloudId=4b42cf18-af4c-439e-9fd9-30a426fcd9ea key=MOBILE

Active SDLC work is tracked in Jira project **MOBILE** ("BTC-Mobile"). `/start-session` reads the line above to pull pending issues. `tasks/todo.md` is a historical archive — never write to it.
Workflow transitions (global): In Progress=21, In Review=31, Done=41.

## Dev flow (mandatory)
Every feature: `/build <story>` → approve PLAN.md → `/implement` (TDD pipeline) → `/ship`. No ad-hoc feature coding. Exception: pure infra/wiring chores and one-line fixes.

## Project-scoped skills (Android — the global /check /test /dev commands point at the Python backend and do NOT work here)
- `/gradle-check` — `./gradlew :app:compileDebugKotlin` (fast compile gate).
- `/android-test` — `./gradlew :app:testDebugUnitTest` + fix loop.
- `/run-app` — `installDebug` → launch `com.gshashank.btcagent/.MainActivity` (adb at `~/Library/Android/sdk/platform-tools/adb`, not on PATH).
- `/ship` — stage→branch→commit→push→PR-to-main→move MOBILE issue to In Review. No merge, no tag.

## Catalog feature flags
Gate every feature behind a runtime **catalog** flag. Names are consts in `data/repository/CatalogFlags.kt`. Semantics: present+true=ON, present+false=OFF, absent=OFF via `catalogOn(flag)`. Security-sensitive flags use `catalogOn(flag, default=true)` so a missing/failed fetch falls back to the SAFE path (Option-A inversion, MOBILE-27). Track each feature's catalog id on its Jira ticket.

## Conventions
- Package root `com.gshashank.btcagent`. Layers: `ui/` (auth, gate, navigation, theme), `data/` (network, repository, remote), `di/`, `core/`.
- Data-layer feature stack pattern: `Api.kt` (Retrofit) + `Dto.kt` + `Repository.kt` (iface) + `RepositoryImpl.kt` + sealed `Result.kt`, Hilt-bound in `di/RepositoryModule.kt` / `di/NetworkModule.kt`. Repositories NEVER throw to callers.
- Secrets always gitignored, never staged: `google-services.json`, `local.properties`, `*.keystore`, `*.jks`, `keystore.properties`.
- Git: branch per feature (`feat/MOBILE-NN-slug`), Conventional Commits, PR to main, user merges (no auto-merge).
