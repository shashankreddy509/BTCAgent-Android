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

## Workflow Orchestration

### 1. Plan Mode Default
- Enter Plan mode for ANY non-trivial task (3+ steps or architectural decisions).
- If something goes sideways, STOP and re-plan immediately - don't keep pushing.
- Use plan mode for verification steps, not just building.
- Write detailed specs upfront to reduce ambiguity.

### 2. Subagent Strategy
- Use subagents liberally to keep the main context window clean.
- Offload research, exploration, and parallel analysis to subagents.
- For complex problems, throw more compute at it via subagents.
- One-task subagents for focused execution.

### 3. Self-Improvement
- After ANY correction from the user: capture the pattern in `tasks/feedback.md` and the memory system (`~/.claude/projects/<cwd-slug>/memory/`, indexed by `MEMORY.md`).
- Write a rule for yourself that prevents the same mistake.
- Ruthlessly iterate on these lessons until the mistake rate drops.
- Prior feedback + memory load at session start (via `/start-session`) — rely on them.

### 4. Verification before done
- Never mark a task complete without proving it works.
- Diff behavior between main and your changes when relevant.
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness.

### 5. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "Is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution."
- Skip this for simple, obvious fixes — don't over-engineer.
- Challenge your own work before presenting it.

### 6. Autonomous Bug Fixing (within the ticket-first gate)
- Diagnose autonomously from logs, errors, and failing tests; implement the fix without hand-holding.
- BUT (project rule) file the MOBILE **Bug** ticket FIRST — full detail (symptom, why-bug, root cause, fix, verification) — before/with the fix. Never fix a bug without its ticket.
- CHECK before deciding a catalog is/isn't needed (don't assume). Autonomy is in the execution, not in skipping the ticket/catalog gates.
- Fix failing CI tests proactively; point at the failure, then resolve it.

## Task Management
1. **Plan First**: Use `/build` → the planner writes **PLAN.md** (no hand-written todo). For ad-hoc work, use plan mode. `tasks/todo.md` is an archive — NEVER write to it.
2. **Verify Plan**: Get the plan approved before implementing (`/implement` only after PLAN.md is approved).
3. **Track Progress**: Use the in-session task tools and the Jira **MOBILE** ticket lifecycle (In Progress → In Review → Done with the PR).
4. **Explain Changes**: High-level summary at each step.
5. **Document Results**: Record the outcome on the Jira ticket / PR (not todo.md).
6. **Capture Lessons**: Update `tasks/feedback.md` + the memory system for corrections (not a separate lesson file).

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Touch minimal code.
- **No Laziness**: Find the root cause. No temporary fixes. Senior-developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.
- **Self-Improvement**: Learn from mistakes and update the rules (feedback.md + memory).
