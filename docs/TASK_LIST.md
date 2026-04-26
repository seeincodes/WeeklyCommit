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
- [x] PITest wired on `service.statemachine.*` package; nightly CI job

### 6. Backend: REST API + DTOs
References: [MVP1], [MVP2], [MVP6], [MVP9], [MVP10], [MVP13], [MVP17], [MVP22]

- [x] Plans controller: `GET /plans/me/current`, `GET /plans`, `POST /plans`, `POST /plans/{id}/transitions`, `PATCH /plans/{id}`, `GET /plans/team`
- [x] Commits controller: `GET /plans/{planId}/commits`, `POST /plans/{planId}/commits`, `PATCH /commits/{id}`, `DELETE /commits/{id}`, `POST /commits/{id}/carry-forward`
- [x] Reviews controller: `POST /plans/{id}/reviews`, `GET /plans/{id}/reviews`
- [x] Rollup controller: `GET /rollup/team` — member card builder, flag computer, byOutcome aggregator *(headline aggregates + member cards + 4-flag set done; `byOutcome` deferred — needs RCDO metadata that arrives with group 7's RCDO client)*
- [x] Null-manager handling: rollup with `manager_id IS NULL` returns empty; `GET /admin/unassigned-employees` surfaces the list for HRIS fix-up
- [x] Audit controller: `GET /audit/plans/{id}` with self-or-manager authz *(matches existing ManagerReviewService.listReviews loose rule: any MANAGER role; tighten both to "direct manager only" together — see follow-up below)*
- [x] **Follow-up:** harmonize `AuditService.findForPlan` and `ManagerReviewService.listReviews` to "self-or-direct-manager-or-ADMIN" *(both now share the same cheapest-first authz: owner-equality and ADMIN short-circuit before any DB hit; MANAGER role gates an EmployeeRepository lookup that confirms direct-manager-of-plan-owner. Peer managers and unassigned-employee plans are rejected for non-ADMIN, matching USER_FLOW.md row 366-367.)*
- [x] Admin controller: `POST /admin/notifications/dlt/{id}/replay` *(synchronous send-and-delete in one tx; pins DLT payload contract = NotificationEvent JSON shape, which group 7's NotificationClient must obey when writing rows)*
- [x] OpenAPI spec generated; committed to `libs/contracts/openapi.yaml` *(code-first via springdoc; `OpenApiSpecGenerationIT` round-trips the runtime spec against the committed file -- drift fails CI, regenerate with `./mvnw verify -Pgen-openapi`)*
- [x] `openapi-typescript` wired; TS regenerates on spec change *(committed `libs/contracts/generated/types.d.ts`; drift script `verify:ts` in `libs/contracts/package.json`. **Java-client generation via `openapi-generator-maven-plugin` deferred to group 7** -- the only consumer of generated Java types is the RcdoClient/NotificationClient pair landing then, so wiring it now would be premature plumbing)*
- [x] MapStruct mappers; null-safety unit-tested *(WeeklyPlan, WeeklyCommit, ManagerReview -- each test covers fully-populated entity, null fields pass through, null entity -> null DTO)*

### 7. Backend: integrations (RCDO + notification-svc)
References: [MVP3], [MVP14], [MVP15]

- [x] `RcdoClient` WebClient wrapper; Resilience4j retry + circuit breaker *(WireMock-driven contract tests; 404 -> Optional.empty before retry sees it; 5xx propagates so AOP can apply backoff)*
- [x] `NotificationClient` WebClient wrapper; retry + circuit breaker; DLT write on permanent failure *(layered: NotificationClient handles status-code mapping (202/409 success, 400 -> NotificationValidationException, 5xx propagate); ResilientNotificationSender wraps with Retry + CircuitBreaker programmatically and writes DLT row on retries-exhausted / circuit-open. Never throws -- the state-transition tx already committed when the dispatcher fires)*
- [x] WireMock contract tests for both clients *(RcdoClientTest + NotificationClientTest pin request/response shapes; RcdoClientResilienceIT proves the @Retry @CircuitBreaker annotations + application.yml config wire through the Spring proxy by counting 3 wire calls under 503. Fixture-validation against docs/spikes/*.json deferred -- those are explicit stub data ("Inputs are invented") flagged for replacement once the real upstream services are reachable)*
- [x] CloudWatch alarm definitions (Terraform): DLT < 1h rule, circuit-breaker-open rule *(infra/terraform/monitoring module: DLT alarm reads `weekly_commit.notification.dlt.recent_count` (gauge published by `DltMetricsPublisher` every 60s); circuit-breaker alarms read auto-published `resilience4j.circuitbreaker.state{name=notification|rcdo,state=open}`. Dimension scopes to `application=weekly-commit-service`. Validates with `terraform validate` against AWS provider 5.x.)*
- [x] `AdminReplayController` integration test *(AdminDltReplayIT closes the loop: drives a real failure through ResilientNotificationSender against WireMock, asserts DLT row appears with PLAN_LOCKED + payload, calls POST /admin/notifications/dlt/{id}/replay, verifies row is deleted and a successful retry POST hit notification-svc. Uses a @TestConfiguration to bypass the @Profile("prod") gate without flipping global profile)*

### 8. Backend: scheduled jobs + Shedlock
References: [MVP4] (auto-lock half), [MVP11], [MVP16]

- [x] Shedlock JDBC provider wired to `shedlock` table *(SchedulingConfig with @EnableSchedulerLock(defaultLockAtMostFor=PT10M); JdbcTemplateLockProvider.usingDbTime() so lock TTL compares against the DB clock, immune to per-pod JVM-clock skew. shedlock table created in V6 migration.)*
- [x] `AutoLockJob` hourly cron; integration test with two simulated pods *(@Scheduled + @SchedulerLock(name=AutoLockJob, lockAtMostFor=PT5M, lockAtLeastFor=PT30S); cutoff math `now - cutoffHours -> Monday on/before`; per-plan failure caught so a single optimistic-lock conflict doesn't abort the batch. ShedlockTwoPodsIT uses two JdbcTemplateLockProvider instances against the same DB to prove serialization.)*
- [x] `ArchivalJob` nightly cron; `reconciledAt < now - 90d` selection *(02:00 UTC; transitions RECONCILED -> ARCHIVED via the state machine; threshold = clock.instant().minus(90, DAYS); per-plan failure isolation matches AutoLockJob.)*
- [x] `UnreviewedDigestJob` Monday 09:00 UTC; skip-level grouping; notification-svc digest send *(grouping logic: plan -> owner -> direct manager -> skip-level manager via EmployeeRepository; null managers go to "unmanaged"/"no-skip-level" buckets logged WARN. Returns DigestRunSummary record. **Dispatch deferred** -- a TODO(group-11) marker explains why NotificationSender is intentionally not extended in this scope.)*
- [x] All threshold comparisons use application `Instant`, verified by lint rule (`NOW\(\)` grep in migration + code scan) *(scripts/lint-now-thresholds.sh: greps src/main/java + db/migration for `\bNOW\b`/`\bCURRENT_TIMESTAMP\b`, allowlists `DEFAULT now(...)` column defaults, SQL/Java comments, Java time-API `.now(`, and `"now"` map keys; baseline audit found 0 forbidden uses (3 legitimate column defaults in V4/V5/V7). Wired into `mvnw test` via NowThresholdLintTest so CI catches drift automatically.)*
- [x] Job success/failure counters → Micrometer → CloudWatch *(JobMetrics @Component wraps each job's run() with Timer.Sample + Counter; emits `weekly_commit.scheduled.job.runs_total{job,outcome}` and `weekly_commit.scheduled.job.duration_seconds{job,outcome}`. Outcome label fires per-job-level throw, not per-batch-item failure. Picked up by the CloudWatch registry from group 7; alarm wiring deliberately deferred -- this commit is observability-only.)*

### 9. Frontend: scaffold + shared singletons
References: [MVP18], [MVP19]

- [x] `apps/weekly-commit-ui` Vite 5 + React 18 + TypeScript strict *(scaffold lives at `apps/weekly-commit-ui/`: `vite.config.ts`, `tsconfig.json` extending root base with `strict`/`noImplicitAny`/`strictNullChecks`/`noUncheckedIndexedAccess` re-affirmed, `index.html`, `src/main.tsx` standalone entry, `src/WeeklyCommitModule.tsx` federated stub. Deps pinned per ADR-0003: react ^18.3.1, react-dom ^18.3.1, react-router-dom ^6.26.2, @reduxjs/toolkit ^2.2.7, vite ^5.4.0, @vitejs/plugin-react ^4.3.0, typescript 5.6.2. `yarn install` not run -- toolchain mismatch (system yarn 1.22 vs packageManager yarn@4.5.0); CI runs the install.)*
- [x] Module Federation plugin (`@originjs/vite-plugin-federation`) configured; `exposes: { './WeeklyCommitModule': ... }`; `shared: { react, react-dom, react-router-dom, @reduxjs/toolkit }` with `eager: false` *(plugin pinned at ^1.3.5; `name: 'weekly_commit'`, `filename: 'remoteEntry.js'`, port 4184 (strictPort), shared singletons + RTK Query also `eager: false` per ADR-0003. Build config uses `target: 'esnext'`, `modulePreload: false`, `cssCodeSplit: false` so host loads remote CSS in one fetch.)*
- [x] Tailwind + Flowbite React; baseline theme integration with host design system (or Headless UI fallback) *(devDeps added: tailwindcss ^3.4.0, postcss ^8.4.0, autoprefixer ^10.4.0, flowbite ^2.5.0; runtime dep flowbite-react ^0.10.0. New files: `tailwind.config.ts` (uses `flowbite-react/tailwind` preset; empty `theme.extend` reserved for host preset injection per ADR-0004), `postcss.config.js`, `src/index.css` (only the three @tailwind directives; CLAUDE.md "utility classes only" lock). `main.tsx` imports `./index.css` and wraps `<HashRouter>` in `<Flowbite>` provider with default theme -- host-token override happens via the Tailwind preset, not the Flowbite `theme` prop (ADR-0004 primary path). `WeeklyCommitModule` placeholder now uses a Flowbite `<Card>` so subtask 6's Playwright smoke can confirm Flowbite + Tailwind both render. `yarn install` not run -- toolchain mismatch as documented in subtask 1.)*
- [x] ESLint 9 + Prettier 3.3; `prettier --check` and `eslint . --max-warnings=0` in CI *(flat-config `apps/weekly-commit-ui/eslint.config.js` (ESM, `tseslint.config(...)`) wires `@eslint/js` recommended + `typescript-eslint` recommendedTypeChecked + stylisticTypeChecked + `eslint-plugin-react` recommended/jsx-runtime + `eslint-plugin-react-hooks` recommended + `eslint-plugin-react-refresh` only-export-components. Hard ignores: `dist`, `node_modules`, `*.config.js`, `*.config.ts` (config files don't belong to the src/ TS project the typed parser needs). Project-scoped block targets `src/**/*.{ts,tsx}` with `parserOptions.project: ./tsconfig.json` and `tsconfigRootDir: import.meta.dirname`. `@typescript-eslint/no-unused-vars` allows `^_`-prefixed args. App scripts add `lint:fix`, `format:check`, `format:write`; existing `lint` keeps `--max-warnings=0` for CI strictness. Prettier config lives at repo root only (`.prettierrc.json`); no app-level fork. devDeps pinned: eslint ^9.10, @eslint/js ^9.10, typescript-eslint ^8.5 (unified parser+plugin), eslint-plugin-react ^7.36, eslint-plugin-react-hooks ^4.6.2, eslint-plugin-react-refresh ^0.4.10, globals ^15.9, prettier ^3.3.3. `yarn install` not run -- toolchain mismatch as documented in subtask 1.)*
- [x] `vitest.config.ts` + coverage-v8; `@testing-library/react` + `@testing-library/jest-dom` *(devDeps added: vitest ^2.1.0, @vitest/coverage-v8 ^2.1.0 (version-locked per Vitest's own constraint), @testing-library/react ^16.0.0, @testing-library/jest-dom ^6.5.0, @testing-library/user-event ^14.5.0, jsdom ^25.0.0. Single config -- `test:` block extends `apps/weekly-commit-ui/vite.config.ts` (no parallel `vitest.config.ts`) so the `define` block stays single-source for both build and test. `/// <reference types="vitest" />` at file top types the `test` field. Coverage: v8 provider (CLAUDE.md lock), text + lcov reporters, 80% thresholds on lines/statements/branches/functions per docs/TESTING_STRATEGY.md (≥ 80% gate); excludes `main.tsx` (Playwright owns), `*.d.ts`, test files, `setupTests.ts`. Setup file `src/setupTests.ts` imports `@testing-library/jest-dom/vitest` for matcher registration. Sample test `src/WeeklyCommitModule.test.tsx` uses `MemoryRouter` (not `HashRouter`) and asserts both `data-testid` hooks; intended to be replaced when group 11 surfaces land. `yarn install` not run -- toolchain mismatch as documented in subtask 1.)*
- [x] Playwright smoke test suite skeleton *(devDep `@playwright/test` ^1.47.0 added. New files: `apps/weekly-commit-ui/playwright.config.ts` (single Chromium project, `webServer` runs `yarn dev` on :4184 reusing existing server locally / fresh in CI, `testDir: ./tests/playwright`, `trace: on-first-retry`), `apps/weekly-commit-ui/tests/playwright/smoke.spec.ts` (two tests asserting `data-testid` hooks: placeholder route renders with version stamp; nested `/weekly-commit/*` paths still mount). `.gitignore` extended with Playwright artifact dirs (`/test-results/`, `/playwright-report/`, `/blob-report/`, `/playwright/.cache/`). README gained a "Smoke tests (Playwright)" section. Tests target the standalone-isolation path via `<HashRouter>` (URLs use `/#/weekly-commit`) -- federated-inside-host coverage is the Cypress + Cucumber suite per CLAUDE.md tech-stack lock. Browsers NOT downloaded (developer runs `playwright install chromium` post-`yarn install`, ~150MB); `yarn install` not run -- toolchain mismatch as documented in subtask 1.)*
- [x] Sentry init + RUM route-enter ping *(`@sentry/react` ^8.30.0 added; init lives in `src/main.tsx` gated on `VITE_SENTRY_DSN` so we don't double-init when the host has already booted Sentry. `release: __WC_GIT_SHA__` ties RUM events to the deployed bundle. `browserTracingIntegration` provides route-enter pings; `tracesSampleRate: 0.1`. Standalone-only entry point -- production-federation Sentry runs in the host context.)*
- [x] Version-stamp build: git SHA injected into remoteEntry via Vite define *(`vite.config.ts` reads `process.env.GIT_SHA` and emits `__WC_GIT_SHA__` (+ `__WC_BUILD_TIME__`) via `define`; falls back to `'dev'` locally. New env var added to `.env` and `docs/TECH_STACK.md` (build-time only, no `VITE_` prefix because it's consumed in node context, not browser code). Surfaces in the placeholder UI as `data-testid="version"` for the Playwright smoke (sibling subtask).)*

### 10. Frontend: RTK Query + typed client
References: [MVP3], [MVP13]

- [x] `libs/rtk-api-client` typed hooks generated from `libs/contracts` OpenAPI types *(hand-written endpoints typed via `operations[opId]` indexing — codegen reconsidered when endpoint count exceeds ~40. All 17 OpenAPI endpoints wired with per-endpoint tag invalidation. 29 vitest tests covering envelope unwrap, 409 retry, slice reducers, and endpoint contracts via MSW.)*
- [x] Tags: `Plan`, `Commit`, `Review`, `Rollup`, `Audit`, `RCDO` *(RCDO tag declared but unused — first endpoint lands in group 11 with `<RCDOPicker />`)*
- [x] `keepUnusedDataFor` per tag: Rollup 60s + refetchOnFocus, RCDO 600s *(api-level default 60s + refetchOnFocus + refetchOnReconnect via API_CONFIG; explicit `keepUnusedDataFor: 60` on `getTeam` and `getTeamRollup` for documentation; RCDO 600s override added with the first RCDO endpoint in group 11.)*
- [x] Global 409 middleware → `<ConflictToast />` + refetch affected tags *(implemented as a baseQuery enhancer (`withConflictRetry`) rather than middleware so the retry sees a clean response and the conflictToastSlice fires before the lifecycle resolves. Server emits `CONFLICT_OPTIMISTIC_LOCK` per `GlobalExceptionHandler.java`. The `<ConflictToast />` component itself ships in group 11.)*
- [x] Local Redux slice: draft form state, optimistic reorder *(`apps/weekly-commit-ui/src/store/draftFormSlice.ts` — editingCommitId, dirty, optimisticOrder with start/cancel/markDirty/reorder/commit/rollback transitions. Wired into the configured store at `apps/weekly-commit-ui/src/store/index.ts` along with the api slice + conflictToast.)*

### 11. Frontend: IC surfaces
References: [MVP1], [MVP2], [MVP4], [MVP5], [MVP6], [MVP7], [MVP8], [MVP21]

- [x] Routes: `/weekly-commit/current`, `/weekly-commit/history` *(react-router v6 nested `<Route path="weekly-commit">` with `<Route index>` redirecting to `current`. Page shells under `apps/weekly-commit-ui/src/routes/{CurrentWeekPage,HistoryPage}.tsx` -- the state-aware `<WeekEditor />` lands inside `CurrentWeekPage` in subtask 2. Playwright smoke + Vitest routing tests both updated to the new shape; eslint ignore extended to `coverage/` so the v8 lcov-report stops tripping typed rules.)*
- [x] `<WeekEditor />`: state-aware (blank state, DRAFT, LOCKED pre-day-4 read-only, LOCKED reconciliation mode, RECONCILED read-only) *(`apps/weekly-commit-ui/src/components/WeekEditor.tsx` -- owns the `useGetCurrentForMeQuery` fetch, routes to one of five modes (`BlankState`, `DraftMode`, `LockedReadOnly`, `ReconcileMode`, `ReconciledSummary`) plus loading + error fallbacks. Mode children are stubs with `data-testid` markers; subtasks 3-9 land their real surfaces in place. The reconcile-eligibility check is inline UTC math (`weekStart + 4d`) with a `TODO(subtask-10)` flag to swap in the IANA-aware helper. `now: Date` injected as a prop so tests can pin the clock and so the TZ helper has a single replacement site. 7 vitest cases cover loading / 404→blank / DRAFT / LOCKED-pre-day-4 / LOCKED-reconcile / RECONCILED / non-404 error -- all paths exercise the mock'd `useGetCurrentForMeQuery` against a real Redux store so middleware drift would surface.)*
- [x] `<RCDOPicker />` typeahead with 4-level breadcrumb; stale-cache banner on RCDO outage *(Presentational typeahead in `apps/weekly-commit-ui/src/components/RCDOPicker.tsx`. Local case-insensitive substring filter against an `outcomes` prop -- the data hook stays in the parent so the picker is trivially unit-testable. ARIA combobox + listbox + option semantics for keyboard users; Enter/Space selects. Breadcrumb separator is " › " (single right-pointing angle quotation mark) per ADR-0001 + `RollupResponse.java`. `isStale` prop renders the cached-data banner described in MEMO Known Failure Modes. RCDO types live in `libs/rtk-api-client/src/rcdo.ts` (re-exported from the lib's index) so the future RTK Query endpoint slots in alongside without churn. **TODO(group-11-rcdo-integration)**: the v1 picker reads from a stub source today -- the parent will swap to the real RTK Query hook once subtask 11a lands the backend pass-through endpoint. 7 vitest cases cover input + filter + breadcrumb + selection + empty + banner show/hide.)*
- [x] `<ChessTier />` vertical spine (Rock/Pebble/Sand) *(`apps/weekly-commit-ui/src/components/ChessTier.tsx` -- presentational container that groups commits into three `role="group"` sections (ROCK > PEBBLE > SAND) with descending visual weight (border thickness + padding) so the chess metaphor reads at a glance. Within a tier, commits sort by `displayOrder` ascending. Top Rock is computed here once (lowest `displayOrder` ROCK) and passed as `isTopRock` to the parent's `renderCommit` callback -- the container is presentational, the parent owns the row UI for DRAFT vs RECONCILE modes. "No Top Rock" surfaces as a status banner when the ROCK tier is empty (MEMO #7: Top Rock derivation -- "no Top Rock" is itself a manager-facing flag). 8 vitest cases cover ordering, grouping, displayOrder sort, Top Rock identification + ROCK-only constraint, per-tier empty placeholders, and no-Top-Rock banner show/hide.)*
- [x] `<ReconcileTable />` keyboard-first; DONE/PARTIAL/MISSED selection; per-commit `actualNote` *(`apps/weekly-commit-ui/src/components/ReconcileTable.tsx` -- one row per commit, each row a radiogroup of DONE/PARTIAL/MISSED + an actualNote textarea (the only mutable fields in reconcile mode per [MVP5]). Per-row `onUpdate(commitId, patch)` callback hands the parent a clean shape to translate into `PATCH /commits/{id}`. ArrowUp/ArrowDown on a status radio jumps between rows preserving the column so reviewing the same status across rows is one keystroke each -- the keyboard-first commitment per the brief. radioRefs are a per-instance `useRef<Record<key, HTMLInputElement | null>>` keyed by `${commitId}:${status}` -- simplest approach without introducing a context provider for one self-contained component. 7 vitest cases cover row count + headers, radio set per row, current-status check, status-change patch, note-change patch, ArrowUp/ArrowDown row nav, and empty placeholder.)*
- [ ] `<ReflectionField />` ≤500 chars, plain text, char counter
- [ ] `<CarryForwardRow />` + "carry all" action
- [ ] `<CarryStreakBadge />` streak ≥2 badge, ≥3 stuck flag styling
- [ ] `<StateBadge />` + next-action hint
- [ ] TZ handling: DB/UTC ↔ employee IANA via `Intl.DateTimeFormat`

### 11a. Backend: RCDO pass-through endpoints (follow-up from group 11 subtask 3)
References: [MVP3], unblocks `<RCDOPicker />` real wire-up

Surfaced while building `<RCDOPicker />` in group 11 -- the UI cannot call the upstream RCDO service directly (CORS + service-token issue), and MEMO #6 describes the backend as the broker. The picker is shipped against a typed stub source; this subtask wires the real path.

- [ ] Spring controller `GET /api/v1/rcdo/supporting-outcomes?orgId=&active=true` proxying to existing `RcdoClient.listSupportingOutcomes(...)`
- [ ] Spring controller `GET /api/v1/rcdo/supporting-outcomes/{id}?hydrate=full` proxying to existing `RcdoClient.getById(...)`; 404 on `SUPPORTING_OUTCOME_NOT_FOUND` per ADR-0001
- [ ] Pass-through DTO matches the `SupportingOutcome` shape in `libs/rtk-api-client/src/rcdo.ts` (BUT remove the trailing rcdo. lib's mock-source once the RTK endpoint is real)
- [ ] OpenAPI spec extended with both endpoints; `libs/contracts/openapi.yaml` + TS regen
- [ ] RTK Query endpoints `useGetSupportingOutcomesQuery` + `useGetSupportingOutcomeByIdQuery` with `keepUnusedDataFor: 600` per the `RCDO` tag
- [ ] `<RCDOPicker />` parent (the WeekEditor draft mode) swaps the stub source for the RTK hook; remove `TODO(group-11-rcdo-integration)` markers
- [ ] WireMock contract test asserts the controller's response shape matches the stub fixture in `docs/spikes/rcdo-sample-responses.json`

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
