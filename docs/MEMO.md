# Architecture Memo — Weekly Commit Module

## Project Summary

Weekly Commit is a micro-frontend module that replaces 15-Five across a 175-person org, forcing every weekly commit to link to a specific Supporting Outcome in the RCDO hierarchy so managers can see alignment (not just output) at roll-up scale. The module ships the full commit → lock → reconcile → review lifecycle, a weekly reflection note for qualitative signal, and carry-forward flagging for commits that keep slipping. It lives inside the existing PA host app as a Vite + Module Federation remote, backed by a single Spring Boot service and Postgres 16.

## Key Architecture Decisions

### 1. One Spring Boot deployment, not api / scheduler / worker split

We ship a single `weekly-commit-service` deployment running REST controllers, scheduled jobs, and the notification client in one process. Scheduled jobs coordinate via Shedlock so only one pod fires each schedule.

**Why not split:** at 175 users with ~50 peak concurrent and ~75 notifications/day, splitting by profile buys nothing — it adds three deployments, three HPAs, three dashboards, and three failure modes. Horizontal scaling happens on CPU (HPA 70%, min 2 / max 6). We split by profile only when measured load demands it, not speculatively.

### 2. Synchronous notification with DLT fallback, not outbox pattern

After a state transition commits its transaction, we make a synchronous REST call to `notification-svc` through Resilience4j (3 retries, exponential backoff, circuit breaker). If the retries exhaust or the circuit opens, we write a row to `NotificationDLT` with the full payload; a CloudWatch alarm fires on any DLT row < 1h old and an admin replay endpoint (`POST /admin/notifications/dlt/{id}/replay`) requeues it.

**Why not outbox:** the outbox pattern requires a dedicated worker deployment, a polling loop, a `FAILED` state reconciliation, and a replay pathway. Its value is decoupling writes from downstream availability. At ~75 notifications/day, decoupling that tiny volume buys less than the infra cost. DLT + alarm + replay endpoint gives us durable failure records and a recovery path without a second deployment. When notification volume or criticality grows, we move to outbox — not before.

### 3. State machine in service code, not DB triggers or controller logic

A single `@Service WeeklyPlanStateMachine` owns the transition table, guards, and side-effect orchestration. Controllers call `stateMachine.transition(planId, targetState)` and nothing else. Transitions are `@Transactional` and idempotent on `(plan_id, target_state, version)`.

**Why not DB triggers:** triggers are invisible to the test suite, version-controlled separately (or not at all), and couple state logic to the storage engine. Refactoring a trigger-dependent migration is miserable. Service code is testable with Mockito, integration-tested with Testcontainers, and mutation-tested with PITest (nightly, ≥70% mutation score target on this service specifically — the brief cares most about state correctness).

**Why not in controllers:** controllers become fat, every transition path needs its own endpoint, and the transition table ends up implicit across six files. Centralizing makes the lifecycle diagram a 1:1 with the code.

### 4. Optimistic locking on `WeeklyPlan` only, not `WeeklyCommit`

`WeeklyPlan` carries `@Version`. Conflicting updates return HTTP 409; a global RTK Query middleware catches 409, toasts the user, and refetches. `WeeklyCommit` is last-write-wins.

**Why not both:** the two-tab case is genuinely rare for a personal weekly-planning tool, and at the commit level last-write-wins is the user's mental model anyway ("whatever I typed most recently is the plan"). Locking commits adds UI refetch cycles on every save for a conflict that doesn't happen. Locking plans protects the transitions — which is where conflicts actually hurt.

### 5. `DRAFT → LOCKED → RECONCILED` (3 states), collapsing the brief's `RECONCILING`

The original brief writes the lifecycle as `DRAFT → LOCKED → RECONCILING → RECONCILED → Carry Forward`. v6 collapses `RECONCILING` into a UI mode: when a plan is `LOCKED` and `now >= weekStart + 4 days`, the editor opens in reconciliation mode; `actualStatus`, `actualNote`, and `reflectionNote` become mutable. The server state stays `LOCKED` until submission.

**Why collapse:** nothing in the domain logic actually transitions *into* `RECONCILING` — the IC just starts editing fields that were already mutable by time-window. A distinct state that gates nothing adds a transition, an audit entry, an event, and an extra notification send with zero behavior change. The test matrix also doubles. v2 can restore it if stakeholder ratification (Q17) demands it. **Carry-forward** is preserved as a user action, not a state — it creates new commits in the *next* week's `DRAFT`, which is the correct model.

### 6. RCDO hierarchy consumed, not owned; labels hydrated at query time

Commits store only `supportingOutcomeId`. On every read, the backend calls `GET /rcdo/supporting-outcomes/{id}?hydrate=full` (or the active-list variant for pickers), and the frontend caches via RTK Query with `keepUnusedDataFor: 600`.

**Why not cache server-side in v1:** caching inside `weekly-commit-service` introduces invalidation questions (when does RCDO renaming propagate?) and an extra infrastructure dependency. RTK Query's 10-minute client cache is long enough for interactive usage and short enough to pick up RCDO edits by next route entry. If RCDO read latency shows up in P95 tracing, we add a server cache — not before.

### 7. Top Rock derived, not stored

`topRock` = first `Rock` by `displayOrder`, or null. Computed in `DerivedFieldService` and applied uniformly in response mappers. IC controls it via drag-reorder in the editor.

**Why derived:** storing it means a second source of truth that can disagree with `displayOrder`. Deriving is cheap (one query, already indexed via `(planId, chessTier, displayOrder)`) and has one authoritative answer. "No Top Rock" (null) is a meaningful manager-facing flag on its own — it means the IC has no Rocks, which is itself worth surfacing.

### 8. 1:1 commit → Supporting Outcome linkage, not M:N

A commit has exactly one `supportingOutcomeId`. No junction table in v1.

**Why:** M:N costs a junction table, a set-based UI picker, and rollup queries that double-count. We don't yet have evidence that multi-linkage reflects how people actually work — the intentionality the product exists to create is arguably stronger with 1:1. The data model stays extensible: adding a junction table later is a Flyway migration and a DTO shape change, not a rewrite.

### 9. No event bus (SQS/SNS) in v1

The brief lists SQS/SNS as required cloud tech. We defer it to v2 pending stakeholder ratification (§12 Q16).

**Why defer:** we have exactly one notification consumer (`notification-svc`). An event bus' value is decoupling *n* consumers from publishers; at n=1 with ~75 events/day, the bus is pure overhead — dead-letter queue configuration, message schemas, IAM policies, cost center attribution. The synchronous-send + DLT pattern gives us the reliability we need at our actual volume. When a second consumer arrives (analytics dashboard, HRIS sync), we add the bus.

### 10. Explicit blank state on the current-week route, not side-effect creation

When the IC navigates to `/weekly-commit/current` and no plan exists, the frontend gets a 404 from `GET /plans/me/current` and renders a "Start your week" blank state with an explicit "Create plan" button that POSTs.

**Why not auto-create:** a plan created by navigation is a plan the IC didn't consciously start. The product exists to make weekly planning intentional; silently creating a plan because a route was visited undermines that. `POST /plans` is idempotent on `(employeeId, weekStart)` so there's no race condition from double-tapping the button.

### 11. Group 19 expanded from "polish" into a visual-design pass (2026-04-27)

What [TASK_LIST.md group 19](TASK_LIST.md#19-ux-polish) originally scoped — ConflictToast copy, empty-state illustrations, carry-streak transition animation, reflection-note 480+ char warning, flag tooltips — was a "polish list," not a design system. After dogfooding the IC and Manager surfaces against real seeded data, the verdict was that the surfaces were *functionally* complete but visually generic ("lackluster"): every page was a flat `<Card>` floating on `bg-gray-50`, the chess-tier metaphor was labelled but not visualised, state-meaning was encoded in arbitrary pastel pills, CTAs were undifferentiated, the team rollup had no visual hierarchy.

We expanded group 19 into a real visual redesign across the IC + Manager surfaces, while keeping the original 5 polish items in scope (empty-state illustrations and flag-glyph chips were absorbed into the wider design pass).

**What changed in this pass:**

  - **Tokens.** Added semantic colour tokens (`brand`, `rock`, `pebble`, `sand`, `warn`, `danger`, `ok`), a 4-step type scale (`display`, `title`, `body`, `meta`), and three soft elevation shadows under `theme.extend` in [tailwind.config.ts](../apps/weekly-commit-ui/tailwind.config.ts). Product-specific names (rock vs primary) chosen so host-preset overrides (ADR-0004) don't accidentally clobber them.
  - **App shell.** Single shared `<AppShell>` replaces the per-route `<div className="p-6 bg-gray-50 min-h-screen"><Card>` pattern. Header bar with product mark + nav (Current / History / Team), eyebrow + page title, header slot for state badges and the new `<WeekContextBadge>`, and a footer that absorbs the build stamp (previously rendered in the page body).
  - **Inline icon set.** Authored as plain SVG components (`src/components/icons/`) instead of pulling in lucide-react / @heroicons. Total set < 3 KB, no new top-level dep, sidesteps task 18.3's bundle-size question for icons.
  - **Chess-tier spine, MemberCard, TeamRollup.** Tier-distinct colour rails + chess-piece glyphs; team rollup gains alignment/completion progress meters and a stacked tier-mix bar; member cards gain a severity rail and severity-coloured flag chips.
  - **DraftMode + CommitCreateForm + StateBadge.** Header card pairs StateBadge with the Lock CTA. The placeholder "Edit" button on each draft row was *removed* — it was a no-op that re-saved the unchanged title. Real inline-edit lands as its own feature, not a polish item; deferring is honest, shipping a lying button is not.
  - **Mode panes (Locked, Reconciled, Reconcile, IcDrawer, BlankState).** Token-pass for visual consistency; BlankState gains a tier-glyph illustrative cluster + a primary brand CTA.

**What didn't change:** the original 5 polish items in group 19's task list still need explicit follow-through (ConflictToast copy refresh, carry-streak transition animation, reflection 480+ char warning, flag tooltip *copy*, the explicit empty-state illustration files for History + Team-no-reports). They got absorbed at the structural level but the line-by-line copy / micro-interaction polish is still TODO. Group 19 in [TASK_LIST.md](TASK_LIST.md) was rewritten to reflect the new shape; the redesign sub-items are checked, the remaining polish sub-items remain unchecked for a follow-up branch.

**Why bundle as one expansion rather than 5 separate small changes:** the design tokens, app shell, chess-spine, member-card, and DraftMode all reference each other — splitting them into 5 PRs would have produced 4 PRs of "doesn't make sense without #5." The single-branch / sequenced-commit pattern (foundation → shell → chess → DraftMode → rollup → empty states + mode polish) lets a reviewer land them in dependency order or roll back any single layer cleanly.

**Tech-stack lock honoured throughout:** Tailwind + Flowbite only (no new CSS files / Emotion / styled-components), no new top-level deps, every animation gated `motion-safe:`, every visual-only change preserved every `data-testid` and accessible-name contract referenced by the existing test suites (172/172 vitest pass).

## Processing Strategy

The system has three pipelines — user-driven, scheduled, and rollup — all reading from the same Postgres.

**User-driven lifecycle pipeline.** IC actions flow through the state machine:

```
IC action → REST controller → WeeklyPlanStateMachine.transition()
         → @Transactional body:
             1. Load plan (with @Version check)
             2. Validate guards for target state
             3. Mutate entity + append audit_log row
             4. Commit transaction
         → Post-commit: synchronous notification-svc call
             ├─ success → metric increment, done
             └─ failure → write NotificationDLT row → CloudWatch alarm
```

Commits are edited freely in `DRAFT`. In reconciliation mode (post-day-4 `LOCKED`), only `actualStatus`, `actualNote`, and `reflectionNote` are mutable — enforced by the state machine, not the controller.

**Scheduled pipeline.** Three jobs, all Shedlock-coordinated so exactly one pod runs each schedule:

- **Auto-lock (hourly):** scan `state=DRAFT AND weekStart + cutoff <= now`. Transition each to `LOCKED` via the same state machine path (so notifications fire normally).
- **Archival (nightly):** scan `state=RECONCILED AND reconciledAt < now - 90d`. Transition to `ARCHIVED`. No notification.
- **Unreviewed digest (Monday 09:00 UTC):** scan `state=RECONCILED AND managerReviewedAt IS NULL AND reconciledAt < threshold`. Group by manager's manager (skip-level). Send one digest per skip-level via notification-svc.

All threshold comparisons use application-computed `Instant`. Never `NOW()` in SQL — that makes TZ/DST bugs silent.

**Rollup pipeline.** Manager requests `GET /rollup/team?managerId=&weekStart=`:

1. Query all plans for the `weekStart` where the employee's manager = `managerId`.
2. For each plan, apply `DerivedFieldService` to compute `topRock`, `carryStreak` (cap 52), `stuckFlag`.
3. Aggregate `alignmentPct`, `completionPct`, `tierDistribution`, `unreviewedCount`, `stuckCommitCount`, `byOutcome`.
4. Order members by flag presence first, then name.

Staleness: manager-facing RTK Query tag `Rollup` uses `keepUnusedDataFor: 60` + `refetchOnFocus: true`. Worst-case staleness is ~60s after an IC reconciles.

## Known Failure Modes

**RCDO service unavailable or contract drift.** Day-1 spike validates the contract (A1). Client sits behind a `RcdoClient` interface with Resilience4j; on sustained outage, the picker shows a staleness banner and cached Supporting Outcomes remain selectable (RTK Query cache). Labels showing stale hierarchy names is better than blocking the IC entirely. If RCDO is down at reconcile time, IC can still mark actuals and write reflection — status change doesn't require RCDO.

**notification-svc missing or incompatible.** Day-1 spike validates existence (A6). If absent, ~1 week scope addition to build a minimal sender. Once built: Resilience4j circuit breaker prevents cascading latency; DLT captures everything that would otherwise be lost; admin replay endpoint exists. The worst-case user experience is "I locked my week but the email was late" — not "I locked my week but the transition didn't happen."

**Module Federation version drift with host.** Shared singletons pinned in both host and remote manifests. Weekly smoke test runs `weekly-commit-ui` against host main. Semver bumps on shared libs require coordinated host+remote release. First sign of drift in CI: either hard fail (pin violations) or soft warn (new peer dep).

**TZ / DST edges at week boundaries.** All week math UTC at service layer; UI converts using `Intl.DateTimeFormat` with the employee's IANA TZ from the JWT. Unit tests cover DST transition weeks explicitly (both spring-forward and fall-back). `timezone` claim staleness is capped at 15 min (Auth0 JWT refresh window) and documented in user-facing help as "update your profile if your timezone changed."

**Manager ignores review flow.** At 72h after `reconciledAt`, dashboard flag fires and a Monday skip-level digest goes out. No workflow block — the tool refuses to become an approval gate. If the digest proves insufficient, escalation pattern can extend to 7-day re-digest or HR dashboard in v2.

**Notification-svc extended outage (hours, not minutes).** Circuit breaker keeps retries from stampeding the downstream. DLT accumulates; CloudWatch alarm escalates. When service recovers, on-call runs a bulk replay against the admin endpoint. No plan transition is lost — plans transitioned even when the notification failed.

**`WeeklyPlan` concurrent edit from two tabs.** `@Version` bumps on each save; second-tab save returns 409; RTK Query middleware catches globally, shows a toast, refetches. Commit-level concurrent edits are last-write-wins by design (decision #4).

**Carry-streak walk blowup.** The walk is capped at 52 hops. If a cycle existed in `carriedForwardFromId` (shouldn't, because it's created forward-only, but defensively), the cap terminates. A separate integrity check runs nightly to alarm on any commit whose streak would exceed 52.

**Null-manager employees.** Queries with `manager_id IS NULL` return empty — no crash. Admin report surfaces the list of unassigned employees for HRIS fix-up. JWT refresh picks up new `manager_id` within 15 min (A5).

**Legal rejects 2y audit retention.** Phase 2 rollout is gated on legal sign-off. Fallback is 90-day full deletion. Data model supports either (audit retention is a job config, not a schema choice).

**Flowbite token conflict with host design system.** Day-1 spike confirms `@host/design-system` tokens override Flowbite defaults. Fallback: Headless UI + Tailwind, which the team has built with before.
