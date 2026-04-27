# PRD — Weekly Commit Module

## Overview

A micro-frontend module that replaces 15-Five for weekly planning, enforcing a structural link between every weekly commit and a specific Supporting Outcome in the org's RCDO hierarchy (Rally Cry → Defining Objective → Core Outcome → Supporting Outcome). Ships the full commit → lock → reconcile → review lifecycle with a reflection note and carry-forward flagging.

## Problem Statement

Weekly planning currently happens in 15-Five, disconnected from strategic execution. Managers cannot see whether an IC's weekly work maps to Rally Cries, Defining Objectives, or Outcomes until it's too late to course-correct. Across 175+ employees this produces untracked strategic drift, and qualitative weekly signal (reflection, blockers) is invisible to managers at roll-up scale.

## Target Users

- **IC (Individual Contributor)** — creates/edits weekly commits, reconciles planned vs. actual on Friday, writes a reflection note, carries forward missed/partial work. ~160 people.
- **Manager** — reviews team commits, sees roll-up alignment, reads reflection notes, flags stuck work. ≤50 direct reports per manager in practice. ~15 people.
- **Org Admin** — out of scope for v1.

The module lives inside the existing Performance Assessment (PA) host app and mirrors the Performance Management (PM) remote pattern.

## MVP Requirements

Checklist of must-have features for v1 ship. Each ID is referenced from [TASK_LIST.md](TASK_LIST.md) and [TESTING_STRATEGY.md](TESTING_STRATEGY.md) coverage matrix.

- [x] [MVP1] — IC can create a current-week `WeeklyPlan` from an explicit blank state (`POST /plans`). Idempotent on `(employeeId, weekStart)`. *(BE: [PlansController.createCurrent](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/PlansController.java) + `WeeklyPlanService.createCurrentForEmployee`. UI: [BlankState.tsx](../apps/weekly-commit-ui/src/components/modes/BlankState.tsx) wired to `useCreateCurrentForMeMutation` in commit `4a1eaa7`. Idempotency enforced via the `(employee_id, week_start)` unique constraint in [V1__create_weekly_plan.sql](../apps/weekly-commit-service/src/main/resources/db/migration/V1__create_weekly_plan.sql).)*
- [x] [MVP2] — IC can CRUD `WeeklyCommit` rows in `DRAFT` state: title, linked Supporting Outcome (required), chess tier (Rock/Pebble/Sand, required), optional description, estimated hours, category tags, `displayOrder`. *(BE: [CommitsController.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/CommitsController.java) — POST/PATCH/DELETE plus list. UI: [DraftMode.tsx](../apps/weekly-commit-ui/src/components/modes/DraftMode.tsx) + [CommitCreateForm.tsx](../apps/weekly-commit-ui/src/components/modes/CommitCreateForm.tsx) wired to `useCreateCommitMutation` / `useUpdateCommitMutation` / `useDeleteCommitMutation`.)*
- [x] [MVP3] — RCDO hierarchy is consumed read-only from the upstream service. Commits store `supportingOutcomeId` only; labels hydrate at query time. *(BE pass-through: [RcdoController.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/RcdoController.java) wraps [RcdoClient.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/integration/rcdo/RcdoClient.java). UI: [RCDOPicker.tsx](../apps/weekly-commit-ui/src/components/RCDOPicker.tsx) + [RCDOPickerContainer.tsx](../apps/weekly-commit-ui/src/components/RCDOPickerContainer.tsx). Standalone-dev served by a wiremock stub at [docker-compose.e2e.yml](../apps/weekly-commit-ui/docker-compose.e2e.yml).)*
- [x] [MVP4] — IC can lock the week (`DRAFT → LOCKED`) manually, or system auto-locks at configured cutoff. Commits become immutable except for reconciliation fields. *(Manual: Lock Week button in [DraftMode.tsx](../apps/weekly-commit-ui/src/components/modes/DraftMode.tsx) → `useTransitionMutation({to:'LOCKED'})`. Auto: [AutoLockJob.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/scheduled/AutoLockJob.java). State guard in `WeeklyPlanStateMachine` rejects edits to non-reconciliation fields once LOCKED.)*
- [x] [MVP5] — Reconciliation mode opens on any `LOCKED` plan when `now >= weekStart + 4 days`. IC marks each commit `DONE | PARTIAL | MISSED`, writes `actualNote`, and an optional `reflectionNote` (≤500 chars) on the plan. *(Eligibility: [`isReconcileEligible`](../apps/weekly-commit-ui/src/lib/timezone.ts). UI: [ReconcileMode.tsx](../apps/weekly-commit-ui/src/components/modes/ReconcileMode.tsx) wires [ReconcileTable.tsx](../apps/weekly-commit-ui/src/components/ReconcileTable.tsx) (per-row status + actualNote) + [ReflectionField.tsx](../apps/weekly-commit-ui/src/components/ReflectionField.tsx) with browser-enforced `maxLength={500}` + 480-char warning.)*
- [x] [MVP6] — IC submits reconciliation (`LOCKED → RECONCILED`); per-item and "carry all" buttons create next-week `DRAFT` commits with `carriedForwardFromId` / `carriedForwardToId` set. *(Submit button in [ReconcileMode.tsx](../apps/weekly-commit-ui/src/components/modes/ReconcileMode.tsx) → `useTransitionMutation({to:'RECONCILED'})`. Carry: [CarryForwardRow.tsx](../apps/weekly-commit-ui/src/components/CarryForwardRow.tsx) + `useCarryForwardMutation` POST `/commits/{id}/carry-forward`. Eligibility predicate: [carryEligibility.ts](../apps/weekly-commit-ui/src/lib/carryEligibility.ts).)*
- [x] [MVP7] — Carry-streak derived (walk `carriedForwardFromId`, cap 52). Badge at streak ≥ 2, `stuckFlag` at ≥ 3. Visible on both IC and manager surfaces. *(BE: `DerivedFieldService` walks the chain with a 52-hop cap. UI: [CarryStreakBadge.tsx](../apps/weekly-commit-ui/src/components/CarryStreakBadge.tsx) self-gates at ≥2 (neutral) and ≥3 ("stuck" red). Surfaced in [ReconciledSummary.tsx](../apps/weekly-commit-ui/src/components/modes/ReconciledSummary.tsx) and [IcDrawer.tsx](../apps/weekly-commit-ui/src/components/IcDrawer.tsx).)*
- [x] [MVP8] — Top Rock derived (lowest `displayOrder` Rock on the plan, or null). Surfaced on manager cards; "no Top Rock" itself is a flag. *(Computed centrally in [ChessTier.tsx](../apps/weekly-commit-ui/src/components/ChessTier.tsx) — first ROCK by displayOrder. "No Top Rock yet" banner renders when ROCK tier is empty. Manager card: [MemberCard.tsx](../apps/weekly-commit-ui/src/components/MemberCard.tsx) surfaces the title or "No Top Rock" indicator.)*
- [x] [MVP9] — Manager team roll-up (`GET /rollup/team`) returns alignment %, completion %, tier distribution, per-member cards with Top Rock, tier shape, reflection preview (~80 chars), and flags. Flagged members ordered first. *(BE: [RollupController.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/RollupController.java). UI: [TeamRollup.tsx](../apps/weekly-commit-ui/src/components/TeamRollup.tsx) + [TeamPage.tsx](../apps/weekly-commit-ui/src/routes/TeamPage.tsx) wire `useGetTeamRollupQuery`. Flagged-first sort baked into `TeamRollup`. Reflection preview truncated to 80 chars in [MemberCard.tsx](../apps/weekly-commit-ui/src/components/MemberCard.tsx).)*
- [x] [MVP10] — Manager IC deep-dive drawer shows full week, full reflection, commit history with streak chains, single plan-level comment field. `managerReviewedAt` set on acknowledge. *(UI: [IcDrawer.tsx](../apps/weekly-commit-ui/src/components/IcDrawer.tsx) wires `useGetPlanByEmployeeAndWeekQuery` + `useListCommitsQuery`; comment via [ReviewCommentField.tsx](../apps/weekly-commit-ui/src/components/ReviewCommentField.tsx) → `useCreateReviewMutation`. BE sets `managerReviewedAt` on review create; tag invalidation refetches the plan.)*
- [x] [MVP11] — Unreviewed-72h digest: scheduled Monday 09:00 UTC scan of `state=RECONCILED AND managerReviewedAt IS NULL AND reconciledAt < threshold`; sends skip-level digest via notification-svc. *([UnreviewedDigestJob.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/scheduled/UnreviewedDigestJob.java); cron + threshold-hours configurable in [application.yml](../apps/weekly-commit-service/src/main/resources/application.yml) (`weekly-commit.scheduled.unreviewed-digest-cron`, default `0 0 9 * * MON`). Shedlock-coordinated.)*
- [x] [MVP12] — State transitions validated by server-side `WeeklyPlanStateMachine`. Never in controllers, never in DB triggers. Transitions are `@Transactional`. *([WeeklyPlanStateMachine.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/service/statemachine/WeeklyPlanStateMachine.java). PITest configured at `service.statemachine.*` per [pom.xml](../apps/weekly-commit-service/pom.xml).)*
- [x] [MVP13] — Optimistic locking on `WeeklyPlan` via `@Version`; conflicting mutations return HTTP 409; UI refetches and retries via global RTK Query middleware. *(BE: `@Version` on `WeeklyPlan` entity; `OptimisticLockException` mapped to 409 in `GlobalExceptionHandler`. UI: [conflictRetry.ts](../libs/rtk-api-client/src/conflictRetry.ts) wraps every base query — 409 → toast + auto-retry once.)*
- [x] [MVP14] — Notification send is synchronous after transaction commit, behind Resilience4j (3 retries, exp backoff, circuit breaker). Permanent failures write `NotificationDLT` row; CloudWatch alarm on any DLT row < 1h old. *(Code: [ResilientNotificationSender.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/integration/notification/ResilientNotificationSender.java) + [TransactionAwareNotificationDispatcher.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/integration/notification/TransactionAwareNotificationDispatcher.java). DLT row written on permanent failure. **CloudWatch alarm pending in group 14 (infra).**)*
- [x] [MVP15] — Admin replay endpoint `POST /admin/notifications/dlt/{id}/replay` requeues failed notifications manually. *([AdminController.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/AdminController.java) — synchronous send-and-delete in one tx, `hasRole('ADMIN')`-gated.)*
- [x] [MVP16] — Scheduled jobs (auto-lock, archival, unreviewed digest) coordinated via Shedlock. Threshold comparisons use application-computed `Instant`, never `NOW()` in SQL. *([AutoLockJob](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/scheduled/AutoLockJob.java), [ArchivalJob](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/scheduled/ArchivalJob.java), [UnreviewedDigestJob](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/scheduled/UnreviewedDigestJob.java) — each `@SchedulerLock`-annotated. Repository queries take `Instant` parameters; verified by inspection.)*
- [x] [MVP17] — Audit log records state transitions and manager review events. `GET /audit/plans/{id}` scoped to MANAGER role or self. 2y retention (gated on legal sign-off before Phase 2). *([AuditController.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/AuditController.java) — owner-equality + ADMIN short-circuit before DB hit; MANAGER role triggers a direct-manager-of-plan-owner check. Same harmonized authz as `ManagerReviewService.listReviews`. **Legal sign-off pending Phase 2.**)*
- [x] [MVP18] — Remote built with Vite 5 + Module Federation, exposing `./WeeklyCommitModule`. Served from S3 + CloudFront at `/remotes/weekly-commit/{version}/remoteEntry.js`. Shared singletons `react`, `react-dom`, `react-router-dom`, `@reduxjs/toolkit`, `@reduxjs/toolkit/query` with `eager: false`. *(Federation config in [vite.config.ts](../apps/weekly-commit-ui/vite.config.ts) per [ADR-0003](adr/0003-pm-remote-vite-config-mirror.md). **S3 + CloudFront serving pending in group 14 (infra).**)*
- [x] [MVP19] — Remote renders standalone (Playwright smoke) AND inside the PA host (Cypress + Cucumber against host). *(Standalone: [tests/playwright/](../apps/weekly-commit-ui/tests/playwright/) — green in CI. Federated: [cypress/](../apps/weekly-commit-ui/cypress/) — smoke .feature green; four scenario .features still `@pending` and re-enable post-host-harness, last 13b subtask in [TASK_LIST.md](TASK_LIST.md).)*
- [x] [MVP20] — Auth0 JWT validated on every backend call; manager-scope endpoints require `roles` contains `MANAGER`. JWT carries `sub`, `org_id`, `manager_id` (nullable), `roles`, `timezone`. *([SecurityConfig.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/config/SecurityConfig.java) — OAuth2 resource server with issuer-uri + audience validation. JwtAuthenticationConverter extracts the role claim; AuthenticatedPrincipal pulls the rest. Standalone-dev path uses [E2eJwtDecoderConfig.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/config/E2eJwtDecoderConfig.java) under the `e2e` profile.)*
- [x] [MVP21] — Week boundaries UTC in DB, employee-TZ in UI. All week math at service layer in UTC. *(DB: `week_start` is a `DATE` column treated as UTC at the service layer. UI: [timezone.ts](../apps/weekly-commit-ui/src/lib/timezone.ts) — `isReconcileEligible`, `currentWeekStart`, `formatInstant` all IANA-aware. DST coverage in [timezone.test.ts](../apps/weekly-commit-ui/src/lib/timezone.test.ts).)*
- [x] [MVP22] — Null-manager handling: rollup queries with `manager_id IS NULL` return empty; admin report surfaces unassigned employees. *(BE: rollup query early-returns on null `managerId`; [AdminController.java](../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/AdminController.java) `GET /admin/unassigned-employees`. RTK Query hook `useListUnassignedEmployeesQuery` exists for an admin UI not yet built.)*
- [x] [MVP23] — Flyway migrations V1–V6 run clean from an empty DB. No Hibernate `ddl-auto`. *(V1–V7 in [db/migration/](../apps/weekly-commit-service/src/main/resources/db/migration/); V7 added `employee` table for the harmonized authz check. `application.yml` pins `ddl-auto: validate`. Clean apply verified by `FlywayMigrationIT` under Testcontainers.)*
- [ ] [MVP24] — Kill switch: host-app feature flag falls back to 15-Five link. Removed at Phase 3. *(Cross-remote contract documented in [kill-switch.feature](../apps/weekly-commit-ui/cypress/e2e/kill-switch.feature) with three `@pending @host-contract`-tagged scenarios. Host-side flag implementation is out of this remote's scope; lands when the host harness exists. Tracked in [TASK_LIST.md](TASK_LIST.md) groups 14 + 23.)*

## Final Submission Features

Stretch goals grouped by category. Phase-dependent; see [TASK_LIST.md](TASK_LIST.md) Phase 2/3.

**Polish & UX**
- Keyboard-first reconcile table (arrow nav, Enter to advance)
- WCAG 2.1 AA on commit editor, reconcile table, manager team view
- Explicit blank state copywriting and empty-state illustrations
- `<ConflictToast />` with auto-refetch explanation
- Carry-streak badge animation on transition from ≥2 to ≥3 (becomes the stuck flag)

**Reliability & Ops**
- Mutation testing (PITest) on `WeeklyPlanStateMachine`, nightly CI, ≥70% mutation score
- Runbook: state-machine recovery, DLT replay, scheduled-job re-run, remote rollback, legal escalation
- CloudWatch alarms for 2+ consecutive scheduled-job failures
- Load test: 100 concurrent users with 2x safety margin

**Observability**
- Sentry frontend error tracking + RUM ping on route enter
- Micrometer → CloudWatch metrics with per-transition counters
- Version-stamp remote manifest with git SHA; backend exposes `/actuator/info`

**Evaluation & Submission**
- Appendix A brief-coverage audit signed off
- Brief-deviation deferrals (Outlook Graph, SQS/SNS, RECONCILING state) ratified by stakeholders before kickoff
- Legal sign-off on audit retention policy

## Performance Targets

| Metric | Target | Scope |
|---|---|---|
| `GET /plans/me/current` P95 | < 200 ms | IC common path |
| `GET /rollup/team` P95 | < 500 ms | 50-report manager |
| `POST /plans/{id}/transitions` P95 | < 300 ms | Includes synchronous notification attempt |
| Frontend initial route render | < 1 s | Lazy-loaded routes under `/weekly-commit/*` |
| Remote bundle | Cached `max-age=31536000, immutable` on versioned paths | CloudFront |
| Peak concurrency supported | 50 users (Mon AM / Fri PM) | Load tested to 100 |
| JaCoCo backend coverage | ≥ 80% | Excludes generated code, DTOs, config |
| Vitest frontend coverage | ≥ 80% | Component logic, hooks, reducers |
| Mutation score on state machine | ≥ 70% | PITest, nightly |
| Data volume supported | ~18k plans + ~180k commits / 2y | Indexed for scale |
| Notification throughput | ~75/day | Synchronous send proportionate |
| Max direct reports per manager | ≤ 50 | Rollup query optimized for this bound |

## Scope Boundaries

**In scope (v1):**
- Weekly commit CRUD with RCDO linking (1:1 to Supporting Outcome)
- Chess layer (Rock/Pebble/Sand) + free-form category tags
- Full lifecycle: DRAFT → LOCKED → RECONCILED (+ ARCHIVED)
- Carry-forward with per-item and carry-all controls
- Reflection note (≤500 chars) on each plan
- Manager roll-up with flagged-first ordering
- Manager IC deep-dive drawer + plan-level comment
- Auto-lock, archival, unreviewed-72h digest jobs
- Notification DLT + admin replay
- Micro-frontend integration into PA host via Module Federation
- Auth0 OAuth2 JWT + RBAC on manager endpoints
- Audit log + `GET /audit/plans/{id}` access (MANAGER or self)

**Out of scope (v1):**
- Replacing the RCDO system of record — consume only
- 1:1 meeting tooling, OKR grading, performance review — separate remotes
- Mobile-native app — responsive web only
- Historical backfill from 15-Five — greenfield from launch
- Per-commit comments or approval gating
- AI-generated commit suggestions
- Org-wide analytics dashboard — deferred to v2
- Analytics materialized view — deferred until live-query perf degrades
- Outlook Graph integration — brief deviation, deferred to v2 pending stakeholder ratification (§12 Q15). v1 ships a free-text `relatedMeeting` field as stand-in.
- SQS/SNS event bus — brief deviation, deferred to v2 pending ratification (§12 Q16). v1 uses synchronous notification + Resilience4j + DLT.
- Explicit `RECONCILING` lifecycle state — brief deviation, deferred to v2 pending ratification (§12 Q17). v1 collapses it to a UI mode on `LOCKED` plans past day-4.
- M:N commit-to-outcome linking — 1:1 sufficient in v1; data model extensible if proven wrong.
