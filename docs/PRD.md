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

- [ ] [MVP1] — IC can create a current-week `WeeklyPlan` from an explicit blank state (`POST /plans`). Idempotent on `(employeeId, weekStart)`.
- [ ] [MVP2] — IC can CRUD `WeeklyCommit` rows in `DRAFT` state: title, linked Supporting Outcome (required), chess tier (Rock/Pebble/Sand, required), optional description, estimated hours, category tags, `displayOrder`.
- [ ] [MVP3] — RCDO hierarchy is consumed read-only from the upstream service. Commits store `supportingOutcomeId` only; labels hydrate at query time.
- [ ] [MVP4] — IC can lock the week (`DRAFT → LOCKED`) manually, or system auto-locks at configured cutoff. Commits become immutable except for reconciliation fields.
- [ ] [MVP5] — Reconciliation mode opens on any `LOCKED` plan when `now >= weekStart + 4 days`. IC marks each commit `DONE | PARTIAL | MISSED`, writes `actualNote`, and an optional `reflectionNote` (≤500 chars) on the plan.
- [ ] [MVP6] — IC submits reconciliation (`LOCKED → RECONCILED`); per-item and "carry all" buttons create next-week `DRAFT` commits with `carriedForwardFromId` / `carriedForwardToId` set.
- [ ] [MVP7] — Carry-streak derived (walk `carriedForwardFromId`, cap 52). Badge at streak ≥ 2, `stuckFlag` at ≥ 3. Visible on both IC and manager surfaces.
- [ ] [MVP8] — Top Rock derived (lowest `displayOrder` Rock on the plan, or null). Surfaced on manager cards; "no Top Rock" itself is a flag.
- [ ] [MVP9] — Manager team roll-up (`GET /rollup/team`) returns alignment %, completion %, tier distribution, per-member cards with Top Rock, tier shape, reflection preview (~80 chars), and flags. Flagged members ordered first.
- [ ] [MVP10] — Manager IC deep-dive drawer shows full week, full reflection, commit history with streak chains, single plan-level comment field. `managerReviewedAt` set on acknowledge.
- [ ] [MVP11] — Unreviewed-72h digest: scheduled Monday 09:00 UTC scan of `state=RECONCILED AND managerReviewedAt IS NULL AND reconciledAt < threshold`; sends skip-level digest via notification-svc.
- [ ] [MVP12] — State transitions validated by server-side `WeeklyPlanStateMachine`. Never in controllers, never in DB triggers. Transitions are `@Transactional`.
- [ ] [MVP13] — Optimistic locking on `WeeklyPlan` via `@Version`; conflicting mutations return HTTP 409; UI refetches and retries via global RTK Query middleware.
- [ ] [MVP14] — Notification send is synchronous after transaction commit, behind Resilience4j (3 retries, exp backoff, circuit breaker). Permanent failures write `NotificationDLT` row; CloudWatch alarm on any DLT row < 1h old.
- [ ] [MVP15] — Admin replay endpoint `POST /admin/notifications/dlt/{id}/replay` requeues failed notifications manually.
- [ ] [MVP16] — Scheduled jobs (auto-lock, archival, unreviewed digest) coordinated via Shedlock. Threshold comparisons use application-computed `Instant`, never `NOW()` in SQL.
- [ ] [MVP17] — Audit log records state transitions and manager review events. `GET /audit/plans/{id}` scoped to MANAGER role or self. 2y retention (gated on legal sign-off before Phase 2).
- [ ] [MVP18] — Remote built with Vite 5 + Module Federation, exposing `./WeeklyCommitModule`. Served from S3 + CloudFront at `/remotes/weekly-commit/{version}/remoteEntry.js`. Shared singletons `react`, `react-dom`, `react-router-dom`, `@reduxjs/toolkit`, `@reduxjs/toolkit/query` with `eager: false`.
- [ ] [MVP19] — Remote renders standalone (Playwright smoke) AND inside the PA host (Cypress + Cucumber against host).
- [ ] [MVP20] — Auth0 JWT validated on every backend call; manager-scope endpoints require `roles` contains `MANAGER`. JWT carries `sub`, `org_id`, `manager_id` (nullable), `roles`, `timezone`.
- [ ] [MVP21] — Week boundaries UTC in DB, employee-TZ in UI. All week math at service layer in UTC.
- [ ] [MVP22] — Null-manager handling: rollup queries with `manager_id IS NULL` return empty; admin report surfaces unassigned employees.
- [ ] [MVP23] — Flyway migrations V1–V6 run clean from an empty DB. No Hibernate `ddl-auto`.
- [ ] [MVP24] — Kill switch: host-app feature flag falls back to 15-Five link. Removed at Phase 3.

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
