# Testing Strategy — Weekly Commit Module

## Testing Pyramid

Target distribution across the product:

| Layer | % of suite | Where it runs |
|---|---|---|
| Unit (BE + FE) | ~70% | JVM (JUnit 5 + Mockito); browser (Vitest + RTL) |
| Integration (BE + contract) | ~20% | Testcontainers Postgres; WireMock for RCDO / notification-svc |
| E2E + smoke | ~10% | Playwright (remote in isolation); Cypress + Cucumber/Gherkin (in host) |

Unit tests are cheap and fast; they carry most of the correctness weight. Integration tests cover the boundaries that unit tests can't observe (real Postgres, real state machine with Flyway-applied schema, Shedlock coordination). E2E is intentionally the narrow tip — one `.feature` per core flow is enough because the product has four core flows (§2 of the presearch), and each is a meaningful user-visible promise.

## Coverage Targets

| Layer | Target | Tool | Gate |
|---|---|---|---|
| Backend line coverage | ≥ 80% | JaCoCo | Blocks merge |
| Frontend line coverage | ≥ 80% | Vitest + `@vitest/coverage-v8` | Blocks merge |
| `WeeklyPlanStateMachine` mutation score | ≥ 70% | PITest | Nightly, non-blocking (signal, not gate) |
| Critical flow E2E | 100% of §2 flows | Cypress + Cucumber | Blocks merge |
| Remote standalone smoke | 100% of routes render | Playwright | Blocks merge |
| Contract test pass rate | 100% | WireMock vs. OpenAPI | Blocks merge |

JaCoCo excludes generated code (`target/generated-sources`, MapStruct impls), DTOs (pure getters/setters), and Spring config classes. PITest runs targeted at `com.acme.weeklycommit.service.statemachine.*` — the brief and our own architecture memo (MEMO decision #3) call this the most correctness-critical part of the system. Mutation testing across the whole service would be noisy and slow; scoping it focuses the signal.

## Test Categories

### Backend

**Unit (JUnit 5 + Mockito)**
- Controllers: request → DTO → service call mapping, exception handler 4xx/5xx paths, JWT claim extraction, `{ data, meta }` envelope shaping.
- Services: `WeeklyPlanStateMachine` transition guards (e.g. `DRAFT → RECONCILED` rejected; `LOCKED → RECONCILED` rejected before day-4), idempotency on `(plan_id, target_state, version)`.
- `DerivedFieldService`: Top Rock selection (null, single Rock, multiple Rocks with same `displayOrder`), carry-streak walk (0, 1, 52, 53-chain terminates at cap, cycle defensively terminates).
- MapStruct mappers: round-trip DTO ↔ entity, null-safety on optional fields.
- Resilience4j clients: retry count, circuit breaker open/half-open transitions, DLT write path on permanent failure (all with a faked downstream).

**Integration (`@SpringBootTest` + Testcontainers Postgres 16)**
- Full state-machine path with real Flyway-applied schema: DRAFT → LOCKED → RECONCILED, ARCHIVED archival job.
- `@Version` optimistic locking: two concurrent PATCHes, assert second returns 409.
- Scheduled jobs with Shedlock: auto-lock picks a single pod in a two-pod simulation, archival respects 90-day threshold, unreviewed digest selects correct records.
- DLT happy-path: stub notification-svc to fail, assert row appears in `notification_dlt`, assert CloudWatch metric counter increments.
- Admin replay endpoint: insert DLT row, POST replay, assert row deleted on success.
- Week math: create plans across DST transition weeks (spring-forward and fall-back in America/Los_Angeles and Europe/London), assert week boundaries stable.
- Null-manager rollup: insert employee with `manager_id=null`, assert rollup query returns empty members.
- Audit log: every state transition appends an `audit_log` row with correct `from_state`, `to_state`, actor.

**Contract (WireMock)**
- RCDO client: happy response, 404 on missing outcome, 5xx triggers retry then breaker, malformed JSON triggers mapper error.
- notification-svc client: happy 200, 500 triggers retry chain then DLT, 429 respects Retry-After.

### Frontend

**Unit (Vitest + React Testing Library)**
- `<WeekEditor />` state-aware rendering: blank state on 404, DRAFT editor, LOCKED pre-day-4 read-only, LOCKED post-day-4 reconciliation mode, RECONCILED read-only.
- `<RCDOPicker />` typeahead with keyboard nav, breadcrumb rendering, stale-cache banner on RCDO error.
- `<ReconcileTable />`: keyboard arrow nav, per-commit status selection, dirty-field tracking.
- `<ReflectionField />`: 500-char cap enforced, character counter accurate, plain-text-only (pastes with HTML sanitized).
- `<CarryStreakBadge />`: shows ≥2, flagged at ≥3, tooltip copy.
- `<TeamRollup />` + `<MemberCard />`: flagged-first ordering with mixed flag states, reflection preview truncation at ~80 chars.
- RTK Query global 409 middleware: shows `<ConflictToast />`, triggers refetch of `Plan` + `Commit` tags.
- Redux slice: draft form state, optimistic reorder, reset on successful save.

**Playwright (remote in isolation)**
- Each route renders without a host: `/weekly-commit/current`, `/weekly-commit/history`, `/weekly-commit/team`, `/weekly-commit/team/:employeeId`.
- Remote `remoteEntry.js` serves successfully from local dev build.
- Remote + shared singletons load without duplicate-React warnings when run against a minimal host shell.

**Cypress + Cucumber/Gherkin (in PA host)**
One `.feature` per core flow. Brief requires BDD syntax.

- `commit-entry.feature` — IC creates plan, adds commits, picks RCDO outcome, assigns chess tier, reorders.
- `lock-week.feature` — IC locks manually; separate scenario auto-locks via manipulated clock; notification DLT verified via admin-only test hook.
- `reconcile.feature` — IC reconciles on day-4, writes reflection, carries forward missed items, submits.
- `manager-review.feature` — Manager views rollup, opens drawer, comments, acknowledges; 72h flag scenario using clock skew.

Each scenario runs against ephemeral env with seeded Auth0 JWT for IC and Manager roles.

## CI Integration

GitHub Actions pipelines, one per app plus a shared PR gate:

```
On PR to main:
  ┌──────────────────────────────────────────────────────────────┐
  │ frontend-pr.yml                                               │
  │   setup Node + Yarn                                           │
  │   yarn install --frozen-lockfile                              │
  │   yarn lint           ← ESLint 9, zero warnings               │
  │   yarn typecheck      ← tsc --noEmit strict                   │
  │   yarn format:check   ← prettier --check                      │
  │   yarn test:unit      ← Vitest with coverage                  │
  │   yarn build          ← Vite build, emits remoteEntry.js      │
  │   yarn test:playwright ← remote-in-isolation smoke            │
  └──────────────────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────────────────┐
  │ backend-pr.yml                                                │
  │   setup JDK 21                                                │
  │   mvn -B spotless:check                                       │
  │   mvn -B compile spotbugs:check                               │
  │   mvn -B test                  ← JUnit + Mockito              │
  │   mvn -B verify                ← Testcontainers integration   │
  │   mvn -B jacoco:report jacoco:check  ← 80% gate               │
  │   docker build, scan (trivy), push to ECR on main merge       │
  └──────────────────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────────────────┐
  │ e2e-pr.yml                                                    │
  │   terraform apply ephemeral env                               │
  │   seed Auth0 test users (IC, MANAGER, ADMIN)                  │
  │   run Cypress + Cucumber against host+remote                  │
  │   tear down on completion                                     │
  └──────────────────────────────────────────────────────────────┘

On merge to main:
  argo sync → EKS deploy; S3 upload remote to versioned path; CF invalidate manifest

Nightly (scheduled):
  mutation-test.yml
    mvn -B org.pitest:pitest-maven:mutationCoverage
      -DtargetClasses='com.acme.weeklycommit.service.statemachine.*'
    report uploaded; ≥70% mutation score warning if under
```

Coverage and mutation reports upload as workflow artifacts and surface in PR comments via a small report-bot.

## Requirement Coverage Matrix

Every MVP requirement maps to at least one test suite. Gaps are not allowed at merge time.

| Requirement | Test suite / file | Test type |
|---|---|---|
| [MVP1] IC creates current-week plan from blank state, idempotent | `PlansControllerIT.createPlan_idempotent`; `commit-entry.feature` | BE integration; E2E |
| [MVP2] IC CRUDs commits in DRAFT | `CommitsControllerIT`; `WeekEditor.test.tsx`; `commit-entry.feature` | BE integration; FE unit; E2E |
| [MVP3] RCDO consumed read-only, labels hydrated | `RcdoClientContractTest` (WireMock); `RCDOPicker.test.tsx` | Contract; FE unit |
| [MVP4] Manual lock + auto-lock; commits immutable | `StateMachineTest.lock*`; `AutoLockJobIT`; `lock-week.feature` | BE unit; BE integration; E2E |
| [MVP5] Reconciliation mode opens post-day-4; actual fields mutable | `StateMachineTest.reconcileGuards`; `WeekEditor.test.tsx#reconcileMode`; `reconcile.feature` | BE unit; FE unit; E2E |
| [MVP6] Submit reconciliation; carry-forward per-item + carry-all | `StateMachineTest.submitReconcile`; `CarryForwardIT`; `reconcile.feature` | BE unit; BE integration; E2E |
| [MVP7] Carry-streak derived, cap 52, badge ≥2, stuckFlag ≥3 | `DerivedFieldServiceTest.carryStreak*`; `CarryStreakBadge.test.tsx` | BE unit; FE unit |
| [MVP8] Top Rock derived from lowest displayOrder Rock | `DerivedFieldServiceTest.topRock*`; `MemberCard.test.tsx` | BE unit; FE unit |
| [MVP9] Manager rollup with flags, ordering, reflection preview | `RollupControllerIT`; `TeamRollup.test.tsx`; `manager-review.feature` | BE integration; FE unit; E2E |
| [MVP10] Manager drawer, comment, `managerReviewedAt` set | `ReviewsControllerIT`; `IcDrawer.test.tsx`; `manager-review.feature` | BE integration; FE unit; E2E |
| [MVP11] Unreviewed-72h digest Monday 09:00 UTC | `UnreviewedDigestJobIT` | BE integration |
| [MVP12] State machine in service, transactional | `StateMachineTest` (all); `StateMachineIT` | BE unit; BE integration |
| [MVP13] `@Version` optimistic locking → 409 on conflict | `OptimisticLockingIT`; `ConflictToast.test.tsx` | BE integration; FE unit |
| [MVP14] Synchronous notification with retry, breaker, DLT on failure | `NotificationClientTest` (Resilience4j); `NotificationDltIT` | BE unit; BE integration |
| [MVP15] Admin replay endpoint | `AdminReplayControllerIT` | BE integration |
| [MVP16] Shedlock-coordinated jobs, Instant-based thresholds | `AutoLockJobIT.twoPods`; `ArchivalJobIT`; `UnreviewedDigestJobIT` | BE integration |
| [MVP17] Audit log on state transitions + manager reviews; scoped access | `AuditLogIT`; `AuditControllerIT.accessControl` | BE integration |
| [MVP18] MF remote exposes `./WeeklyCommitModule`, versioned CF path | `build.test.ts` (verifies remoteEntry contents); Playwright remote-in-isolation | FE unit; smoke |
| [MVP19] Remote renders standalone AND inside host | Playwright (standalone); Cypress features (in host) | smoke; E2E |
| [MVP20] Auth0 JWT validated; MANAGER role required on manager endpoints | `SecurityConfigIT.roleEnforcement`; `manager-review.feature#rbac` | BE integration; E2E |
| [MVP21] Week boundaries UTC in DB; employee-TZ in UI | `WeekMathTest` (DST transitions); `WeekEditor.test.tsx#timezone` | BE unit; FE unit |
| [MVP22] Null-manager rollup returns empty; admin report lists them | `NullManagerIT` | BE integration |
| [MVP23] Flyway V1–V6 run clean from empty DB | `FlywayMigrationIT` (Testcontainers fresh DB) | BE integration |
| [MVP24] Kill switch feature flag in host falls back to 15-Five link | `kill-switch.feature` | E2E |

Any new requirement ID added to [PRD.md](PRD.md) MUST add a row to this matrix before the PR merges. CI enforces by grepping MVP IDs in PRD against this table.
