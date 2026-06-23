# Session Feedback — 2026-06-21

## Preferences
- MANDATORY dev flow: every feature via /project:build → approve PLAN.md → /project:implement. No ad-hoc coding.
- "Look into X folder/docs" means READ and report, not start work.
- Wants git discipline: init early, commit per logical step, Conventional Commits.
- Wants PR-to-main workflow; he merges PRs himself, no auto-merge.
- Builds reusable global skills for repeated rituals (e.g. commit-all).
- Wants self-contained design briefs to hand to external tools (Claude Design) before UI build.
- Designs UI externally first; returns to implement once mockups finalized.
- Caveman mode active: terse responses, drop articles/filler.

## Corrections I Made
- Asked to start dev when he only said read docs → just read, then wait.
- Asked about commit.md when I mentioned it → investigate the actual file, explain it.
- Repeatedly said "just read, no writing files" → strictly honor read-only intent.

## What Worked Well
- Stating assumptions briefly then proceeding (git .gitignore hardening, defaults).
- Verifying build green before committing; listing snags fixed.
- Flagging state conflicts (empty PR) and offering options instead of guessing.

## What to Avoid
- Don't ask to start development off a "read the docs" request.
- Don't FF-merge feature branches into main before he reviews — breaks PR flow.
- Don't force-push/reset shared main without explicit per-action approval.

## System Instructions for Future Sessions
- ALL feature development MUST go through the coordinator pipeline. Per screen/feature:
  1. /project:build <feature> → planner writes PLAN.md (no code), 2. user approves PLAN.md,
  3. /project:implement → build-coordinator runs test-writer → implementer → test-runner
  (max 5 retries) → code-review-coordinator → changelog. Never hand-write feature code outside this.
  Exception: pure infra/wiring chores (Phase 0 style) and one-line fixes may skip it.
- On "look into / go through" docs: read + summarize only, then stop.
- Init git and commit per logical step; Conventional Commits; verify build before commit.
- Keep feature work on a branch; open PR to main; never auto-merge or push main directly.
- Never FF-merge into main pre-review; it makes the PR diff empty.
- Treat reset/force-push of main as destructive; require explicit approval each time.
- New skills only show after session reload; invoke directly meanwhile.
- Secrets (google-services.json, local.properties, keystores) always gitignored, never committed.
- When he says "just read / no writing files", do ZERO writes — investigate + report only.
- UI is designed externally (Claude Design); produce a self-contained brief, then wait to resume implementation.
