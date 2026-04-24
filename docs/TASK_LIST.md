# Task List — Weekly Commit Module

Phased work breakdown. Each task group references the [PRD.md](PRD.md) requirement ID(s) it satisfies. Subtask checkboxes represent work items. Do not renumber groups when mid-work — new work appends.

## Phase 1: MVP

### 1. Repo + monorepo scaffold
References: setup prerequisite for [MVP18], [MVP23]

- [x] Initialize git repo; protect `main` (local init done; branch protection requires GitHub remote — configure via repo settings after `git remote add origin`)
- [x] Yarn workspaces + Nx root config (`nx.json`, `package.json` workspaces: `apps/*`, `libs/*`)
- [x] Create workspace dirs: `apps/weekly-commit-ui`, `apps/weekly-commit-service`, `libs/ui-components`, `libs/rtk-api-client`, `libs/contracts`
- [x] Root `.gitignore`, `.editorconfig`, `.nvmrc` (Node 20 LTS), `.tool-versions` (Java 21)
- [x] Commit `docs/` scaffold, `CLAUDE.md`, `.env` template to main via one PR (committed to local `main` in the initial scaffold commit; open as PR once `origin` remote is added)
- [x] GitHub Actions shells for `frontend-pr.yml`, `backend-pr.yml`, `e2e-pr.yml` (lint/test-only initially)

### 2. Day-1 spikes (blocking — assumptions A1, A3, A6 + Flowbite)
References: blockers for [MVP3], [MVP14], [MVP18]

- [x] RCDO contract spike: hit `GET /rcdo/supporting-outcomes`, capture real response shape, reconcile with §3 / [TECH_STACK.md](TECH_STACK.md) *(stubbed — see [ADR-0001](adr/0001-rcdo-contract.md); requires real capture before group 7 ships)*
- [x] notification-svc contract spike: confirm existence + request/response shape; escalate if absent (~1 week scope add) *(stubbed — see [ADR-0002](adr/0002-notification-svc-contract.md); real capture required before group 7 ships)*
- [x] PM remote reference: pull existing `vite.config.ts` Module Federation setup; mirror shape in `weekly-commit-ui` *(stubbed — see [ADR-0003](adr/0003-pm-remote-vite-config-mirror.md); real PM config required before group 9 scaffolds the remote)*
- [x] Flowbite + `@host/design-system` token override spike; fall back to Headless UI + Tailwind if tokens don't override cleanly *(stubbed — see [ADR-0004](adr/0004-flowbite-token-override.md); real spike run at start of group 9)*
- [x] Spike findings written up as ADR in `docs/adr/` (one per spike) *(ADRs [0001](adr/0001-rcdo-contract.md)–[0004](adr/0004-flowbite-token-override.md) all **Proposed (stubbed)**; stubs must be validated before downstream groups ship production)*

### 3. Backend: scaffold + security + baseline
References: [MVP20], [MVP23]

- [x] `apps/weekly-commit-service` Spring Boot 3.3 project; Java 21 toolchain
- [x] Dependencies per [TECH_STACK.md](TECH_STACK.md#backend-appsweekly-commit-service-mavengradle)
- [x] Package layout: `api/`, `domain/`, `repo/`, `service/`, `scheduled/`, `integration/`, `config/`
- [x] Spotless (google-java-format) + SpotBugs configured, wired to `mvn verify`
- [x] Auth0 OAuth2 resource server config; `JwtAuthenticationConverter` extracts `roles`, `sub`, `org_id`, `manager_id`, `timezone`
- [x] `@Auditing` via `AbstractAuditingEntity` base class
- [x] Health + `/actuator/info` version-stamp wired
- [x] Global exception handler mapping: 409 on `OptimisticLockException`, 422 on state guard violations, 403 on RBAC failure
- [x] `{ data, meta }` response envelope standardized

### 4. Backend: data model + Flyway migrations
References: [MVP2], [MVP6], [MVP23]

- [x] `V1__create_weekly_plan.sql`
- [x] `V2__create_weekly_commit.sql`
- [x] `V3__create_manager_review.sql`
- [x] `V4__create_notification_dlt.sql`
- [x] `V5__create_audit_log.sql`
- [x] `V6__indexes_and_constraints.sql` (Top Rock index, carry-streak index, Shedlock table)
- [x] JPA entities + enums (`PlanState`, `ChessTier`, `ActualStatus`)
- [x] Spring Data repositories; custom queries for rollup
- [x] `FlywayMigrationIT` verifies clean apply from empty DB under Testcontainers

### 5. Backend: state machine + DerivedFieldService
References: [MVP4], [MVP5], [MVP6], [MVP7], [MVP8], [MVP12], [MVP17]

- [x] `WeeklyPlanStateMachine` service with transition table: DRAFT→LOCKED, LOCKED→RECONCILED, RECONCILED→ARCHIVED
- [x] Guards: reconciliation mode window `now >= weekStart + 4 days`; archival `reconciledAt < now - 90d`
- [x] Idempotency key `(plan_id, target_state, version)` enforced
- [x] `@Transactional` transitions; `audit_log` append in same tx
- [x] Post-commit notification hook (decoupled from the tx via `TransactionSynchronization`)
- [x] `DerivedFieldService`: Top Rock, carryStreak (52-hop cap), stuckFlag
- [x] Unit tests for every guard + cap boundary *(satisfied by red-green cycles across state machine + DerivedFieldService)*
- [ ] PITest wired on `service.statemachine.*` package; nightly CI job

### 6. Backend: REST API + DTOs
References: [MVP1], [MVP2], [MVP6], [MVP9], [MVP10], [MVP13], [MVP17], [MVP22]

- [ ] Plans controller: `GET /plans/me/current`, `GET /plans`, `POST /plans`, `POST /plans/{id}/transitions`, `PATCH /plans/{id}`, `GET /plans/team`
- [ ] Commits controller: `GET /plans/{planId}/commits`, `POST /plans/{planId}/commits`, `PATCH /commits/{id}`, `DELETE /commits/{id}`, `POST /commits/{id}/carry-forward`
- [ ] Reviews controller: `POST /plans/{id}/reviews`, `GET /plans/{id}/reviews`
- [ ] Rollup controller: `GET /rollup/team` — member card builder, flag computer, byOutcome aggregator
- [ ] Null-manager handling: rollup with `manager_id IS NULL` returns empty; `GET /admin/unassigned-employees` surfaces the list for HRIS fix-up
- [ ] Audit controller: `GET /audit/plans/{id}` with self-or-manager authz
- [ ] Admin controller: `POST /admin/notifications/dlt/{id}/replay`
- [ ] OpenAPI spec generated; committed to `libs/contracts/openapi.yaml`
- [ ] `openapi-generator-maven-plugin` + `openapi-typescript` wired; TS + Java client regenerate on spec change
- [ ] MapStruct mappers; null-safety unit-tested

### 7. Backend: integrations (RCDO + notification-svc)
References: [MVP3], [MVP14], [MVP15]

- [ ] `RcdoClient` WebClient wrapper; Resilience4j retry + circuit breaker
- [ ] `NotificationClient` WebClient wrapper; retry + circuit breaker; DLT write on permanent failure
- [ ] WireMock contract tests for both clients
- [ ] CloudWatch alarm definitions (Terraform): DLT < 1h rule, circuit-breaker-open rule
- [ ] `AdminReplayController` integration test

### 8. Backend: scheduled jobs + Shedlock
References: [MVP4] (auto-lock half), [MVP11], [MVP16]

- [ ] Shedlock JDBC provider wired to `shedlock` table
- [ ] `AutoLockJob` hourly cron; integration test with two simulated pods
- [ ] `ArchivalJob` nightly cron; `reconciledAt < now - 90d` selection
- [ ] `UnreviewedDigestJob` Monday 09:00 UTC; skip-level grouping; notification-svc digest send
- [ ] All threshold comparisons use application `Instant`, verified by lint rule (`NOW\(\)` grep in migration + code scan)
- [ ] Job success/failure counters → Micrometer → CloudWatch

### 9. Frontend: scaffold + shared singletons
References: [MVP18], [MVP19]

- [ ] `apps/weekly-commit-ui` Vite 5 + React 18 + TypeScript strict
- [ ] Module Federation plugin (`@originjs/vite-plugin-federation`) configured; `exposes: { './WeeklyCommitModule': ... }`; `shared: { react, react-dom, react-router-dom, @reduxjs/toolkit }` with `eager: false`
- [ ] Tailwind + Flowbite React; baseline theme integration with host design system (or Headless UI fallback)
- [ ] ESLint 9 + Prettier 3.3; `prettier --check` and `eslint . --max-warnings=0` in CI
- [ ] `vitest.config.ts` + coverage-v8; `@testing-library/react` + `@testing-library/jest-dom`
- [ ] Playwright smoke test suite skeleton
- [ ] Sentry init + RUM route-enter ping
- [ ] Version-stamp build: git SHA injected into remoteEntry via Vite define

### 10. Frontend: RTK Query + typed client
References: [MVP3], [MVP13]

- [ ] `libs/rtk-api-client` typed hooks generated from `libs/contracts` OpenAPI types
- [ ] Tags: `Plan`, `Commit`, `Review`, `Rollup`, `Audit`, `RCDO`
- [ ] `keepUnusedDataFor` per tag: Rollup 60s + refetchOnFocus, RCDO 600s
- [ ] Global 409 middleware → `<ConflictToast />` + refetch affected tags
- [ ] Local Redux slice: draft form state, optimistic reorder

### 11. Frontend: IC surfaces
References: [MVP1], [MVP2], [MVP4], [MVP5], [MVP6], [MVP7], [MVP8], [MVP21]

- [ ] Routes: `/weekly-commit/current`, `/weekly-commit/history`
- [ ] `<WeekEditor />`: state-aware (blank state, DRAFT, LOCKED pre-day-4 read-only, LOCKED reconciliation mode, RECONCILED read-only)
- [ ] `<RCDOPicker />` typeahead with 4-level breadcrumb; stale-cache banner on RCDO outage
- [ ] `<ChessTier />` vertical spine (Rock/Pebble/Sand)
- [ ] `<ReconcileTable />` keyboard-first; DONE/PARTIAL/MISSED selection; per-commit `actualNote`
- [ ] `<ReflectionField />` ≤500 chars, plain text, char counter
- [ ] `<CarryForwardRow />` + "carry all" action
- [ ] `<CarryStreakBadge />` streak ≥2 badge, ≥3 stuck flag styling
- [ ] `<StateBadge />` + next-action hint
- [ ] TZ handling: DB/UTC ↔ employee IANA via `Intl.DateTimeFormat`

### 12. Frontend: Manager surfaces
References: [MVP9], [MVP10]

- [ ] Routes: `/weekly-commit/team`, `/weekly-commit/team/:employeeId`
- [ ] `<TeamRollup />` flagged-first ordering; aggregate stats
- [ ] `<MemberCard />` with Top Rock, tier shape, reflection preview, flags
- [ ] `<IcDrawer />` overlay (preserves team view context); full plan, full reflection, commit history with streak chains
- [ ] Single plan-level comment field; POST to `/plans/{id}/reviews`; `managerReviewedAt` surfaces

### 13. Cross-remote E2E
References: [MVP19], [MVP24]

- [ ] Cypress + Cucumber setup in `apps/weekly-commit-ui/cypress/`
- [ ] `commit-entry.feature`, `lock-week.feature`, `reconcile.feature`, `manager-review.feature`
- [ ] Kill-switch `.feature`: host flag falls back to 15-Five link
- [ ] Test Auth0 users seeded: IC (with manager), IC (null manager), MANAGER, ADMIN
- [ ] Ephemeral env provisioning in `e2e-pr.yml`

### 14. Infra
References: [MVP14], [MVP18], [MVP20]

- [ ] Terraform: EKS deployment + HPA (CPU 70%, min 2 / max 6); ConfigMap for env vars; Secrets wired from AWS Secrets Manager
- [ ] RDS Postgres 16.4 Multi-AZ, gp3 20GB, backup retention 7d
- [ ] CloudFront + S3 origin for remote bundle; versioned path cache policy; manifest `no-cache`
- [ ] CloudWatch alarms: DLT < 1h, scheduled-job ≥2 consecutive failures, HPA capped, RDS CPU > 80%
- [ ] IAM: service role for EKS pod → RDS, CloudWatch, Secrets Manager
- [ ] ArgoCD app manifest; sync policy `automated: { prune: true, selfHeal: true }`
- [ ] Kill-switch feature flag plumbed through host-app config

### 15. Acceptance + definition of done (v1)
References: all [MVPx]

- [ ] Requirement coverage matrix green in CI ([TESTING_STRATEGY.md](TESTING_STRATEGY.md#requirement-coverage-matrix))
- [ ] JaCoCo ≥ 80%; Vitest ≥ 80%; PITest ≥ 70% on state machine
- [ ] Load test at 100 concurrent users; P95 targets met
- [ ] Brief-deviation deferrals ratified (§12 Q15–17)
- [ ] Legal sign-off on audit retention policy (gates Phase 2 but must be started here)
- [ ] Runbook in `docs/runbook.md`: state-machine recovery, DLT replay, scheduled-job re-run, remote rollback, legal escalation

## Phase 2: Polish

### 16. Accessibility
References: WCAG 2.1 AA on commit editor, reconcile table, manager team view

- [ ] Axe CI integration in Playwright + Cypress
- [ ] Keyboard-only user test pass on each surface
- [ ] Screen-reader pass on reconcile table (column headers, row context)
- [ ] Focus management in `<IcDrawer />` overlay

### 17. Observability hardening
References: [MVP14], [MVP16]

- [ ] Per-transition Micrometer timers + counters
- [ ] RUM dashboards in CloudWatch
- [ ] Sentry release-tracking tied to git SHA
- [ ] On-call runbook additions for common alarm fire patterns

### 18. Performance
References: Performance Targets section of [PRD.md](PRD.md#performance-targets)

- [ ] Query profiling for `GET /rollup/team` at 50 reports; EXPLAIN ANALYZE on indexes
- [ ] Rollup N+1 audit; add `@EntityGraph` or projection DTO as needed
- [ ] Bundle-size analysis on remote; tree-shake Flowbite imports
- [ ] CloudFront hit-ratio monitoring; cache rules verified

### 19. UX polish
References: "Polish & UX" in [PRD.md](PRD.md#final-submission-features)

- [ ] `<ConflictToast />` copy pass with auto-refetch explanation
- [ ] Empty-state illustrations (blank week, empty team)
- [ ] Carry-streak badge transition animation (≥2 → ≥3)
- [ ] Reflection note 480+ char warning color
- [ ] Flag tooltips with the concrete reason ("Unreviewed for 72h since reconcile")

## Phase 3: Final

### 20. Rollout gating
References: §10 Rollout Plan

- [ ] Phase 0: internal dogfood with 1 team (8–12 people); zero-defect-week gate
- [ ] Phase 1 expansion: Eng + Product (~40 people); <1% error rate; p95 met
- [ ] Phase 2 expansion: full 175+; 15-Five write-path disabled; legal sign-off obtained
- [ ] Phase 3: 15-Five decommissioned; historical export archived to S3

### 21. Reliability & mutation testing
References: "Reliability & Ops" in [PRD.md](PRD.md#final-submission-features)

- [ ] PITest mutation score ≥70% on `WeeklyPlanStateMachine`; block merge if regressed
- [ ] Chaos test: inject notification-svc 10-min outage; verify DLT behavior, alarm, replay recovery
- [ ] Failover drill: RDS Multi-AZ forced failover; verify recovery under load

### 22. Evaluation & submission
References: "Evaluation & Submission" in [PRD.md](PRD.md#final-submission-features)

- [ ] Final brief-coverage audit (Appendix A) signed off by stakeholders
- [ ] v2 candidates scoped and estimated: analytics dashboard, Outlook Graph, outbox/SQS, RECONCILING state restoration
- [ ] Postmortem template prepared for Phase 2 production monitoring window

### 23. Kill-switch removal
References: [MVP24] end-state

- [ ] Remove host-app feature flag
- [ ] Remove 15-Five fallback link
- [ ] Confirm S3 historical export archived
