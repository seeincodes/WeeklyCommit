# Presearch: Weekly Commit Module

**Status:** Draft v6.1 — Ship-It Scope
**Author:** Xian
**Date:** 2026-04-23
**Target:** One-shot implementation

> This version is deliberately scoped for delivery. Items marked **[v2]** are deferred to the next iteration with their rationale preserved. Deferrals of brief-named items are flagged in §12 for stakeholder ratification. Everything in the main body is required for v1 ship.

---

## Glossary

- **RCDO** — the org's strategic hierarchy. Four levels, nested: **R**ally Cry → **D**efining Objective → **C**ore Outcome → **S**upporting Outcome. Every weekly commit links to exactly one Supporting Outcome (the leaf). Higher levels are read-only and sourced from the upstream RCDO service.
- **Chess layer** — the prioritization tier assigned to each commit: **Rock** (strategic, highest priority), **Pebble** (meaningful incremental work), **Sand** (filler, lowest priority). Plus free-form category tags.
- **Top Rock** — the Rock with the lowest `displayOrder` on a plan. Surfaced as the one-sentence summary of an IC's week in the manager team view. Null if no Rocks exist (itself a flag). IC-controlled via drag-reorder in the draft view.
- **Reflection note** — an optional free-text field (≤500 chars) on each `WeeklyPlan`, written by the IC at reconcile time. Surfaced on the manager card preview and in full in the drawer. IC-only writable.
- **Carry-forward** — moving a MISSED or PARTIAL commit from one week's plan into the next week's DRAFT. Creates a new `WeeklyCommit` row with `carriedForwardFromId` pointing to the source; the source gets `carriedForwardToId` set.
- **Carry-streak** — the length of an unbroken carry-forward chain, computed by walking `carriedForwardFromId`. A streak ≥3 is flagged in both IC and manager views.
- **PA host / PM remote** — the existing Performance Assessment host app consumes the Performance Management remote. The Weekly Commit remote mirrors the PM remote pattern.

---

## 0. Open Assumptions (Verify Before Build)

| # | Assumption | Blast radius if wrong |
|---|---|---|
| A1 | RCDO hierarchy is owned by an existing upstream service consumed via REST | Rewrites the data layer |
| A2 | "Chess layer" means Rocks / Pebbles / Sand tiering plus free-form tags | Rewrites prioritization UI and validation |
| A3 | "PA host / PM remote" — PA app consumes PM remote; we mirror that contract | Changes Module Federation manifest shape |
| A4 | Week boundaries are Monday 00:00 → Sunday 23:59 in the user's TZ, UTC in DB | Affects week math and carry-forward |
| A5 | Auth0 JWTs carry `employee_id`, `manager_id`, `org_id`, `timezone`; changes propagate on refresh (≤15min) | Adds user-sync job if missing |
| A6 | `notification-svc` exists as a shared service for mail send | ~1 week scope to build minimal sender if absent |
| A7 | Max direct reports per manager ≤ 50 in practice | Rollup query plan changes if truly higher |
| A8 | Commits link 1:1 to a Supporting Outcome | Junction table added if M:N required |
| A9 | Top Rock is IC-controlled via `displayOrder` | Changes rollup query if rule-based |
| A10 | Every employee has a manager assigned in Auth0 | Null-manager handling if not |

---

## 1. Problem & Goal

### Problem
Weekly planning happens in 15-Five, disconnected from strategic execution. Managers can't see whether weekly work maps to Rally Cries, Defining Objectives, or Outcomes until it's too late to course-correct. Across 175+ employees, this produces untracked drift.

### Goal
Ship a production-ready micro-frontend module that replaces 15-Five and enforces the structural link between every weekly commit and a specific Supporting Outcome in the RCDO hierarchy. The module covers the full commit → lock → reconcile → review lifecycle, captures a weekly reflection note for qualitative signal, and flags commits that keep slipping.

### Non-Goals
- Replacing the RCDO system of record (we consume it)
- Replacing 1:1 meeting tooling, OKR grading, or performance review (separate remotes)
- Mobile-native app (responsive web only)
- Historical backfill from 15-Five (greenfield from launch)
- Per-commit comments or approval gating (keeps tool from becoming micromanagement)
- AI-generated commit suggestions (erodes the intentionality the product exists to create)

### [v2] Deferred from v1
The items below are deferred to v2 iteration. Three of these (Outlook Graph, SQS/SNS, RECONCILING state) were explicitly named in the original brief; their deferral is a scope decision and requires stakeholder ratification via §12 questions #15–17 before v1 kickoff.

- **[brief deviation] Outlook Graph integration** — brief lists this as required tech. No current use case justifies the Graph auth, proxy, and scope work. v1 supports a free-text `relatedMeeting` reference on commits as a stand-in. See §12 Q15.
- **[brief deviation] SQS/SNS event bus** — brief lists this as required cloud tech. At ~75 notifications/day, a synchronous call with DLT fallback is proportionate; SQS buys reliability headroom we don't need yet. See §12 Q16.
- **[brief deviation] `RECONCILING` lifecycle state** — brief writes the lifecycle as `DRAFT → LOCKED → RECONCILING → RECONCILED → Carry Forward`. v6 collapses this to `DRAFT → LOCKED → RECONCILED`: reconciliation is a UI mode that opens when `now >= weekStart + 4 days` rather than a distinct server state, because nothing in the domain logic actually transitions *into* a `RECONCILING` state — the IC just starts editing `actualStatus` fields that were already mutable. Keeping a state that gates nothing adds a transition, an event, and an audit entry with no behavior change. See §12 Q17.
- **Org-wide analytics dashboard** — skip-level trend charts. Valuable but its own project; scope-out preserves ship date. Ship team-level visibility first, let real usage inform the dashboard's shape.
- **Analytics materialized view** — not needed until live-query performance measurably degrades (would land with the dashboard).

---

## 2. Users & Core Flows

### Personas
- **IC (Individual Contributor)** — creates/edits weekly commits, reconciles, writes reflection note, carries forward
- **Manager** — reviews team commits, sees roll-up alignment, reads reflection notes, leaves feedback
- **Org Admin** — not a v1 persona

### Core Flows (v1)

**Flow 1 — Commit Entry (Monday AM)**
1. IC opens module → `GET /plans/me/current` returns current-week plan, or 404
2. If 404: IC sees a "Start your week" blank state with a "Create plan" button → `POST /plans`
3. Adds commit → required: title, linked Supporting Outcome, chess tier (Rock/Pebble/Sand)
4. Optional: description, estimated hours
5. Commits carried forward from prior weeks appear pre-populated with their carry-streak indicator
6. Saves → commit persisted, plan stays DRAFT

**Flow 2 — Lock (Monday EOD / auto-lock)**
1. IC clicks "Lock Week" OR system auto-locks at configured cutoff
2. State `DRAFT → LOCKED`, commits become immutable except for reconciliation fields
3. Synchronous notification call to notification-svc; on failure, write DLT row + alarm

**Flow 3 — Reconcile (Friday)**
1. IC opens plan on or after `weekStart + 4 days` → UI shows reconciliation mode
2. For each commit: marks DONE / PARTIAL / MISSED, writes note
3. IC writes a reflection note on the week (optional, ≤500 chars)
4. MISSED/PARTIAL commits surfaced with per-item "carry to next week" buttons (+ "carry all")
5. Submits → state `LOCKED → RECONCILED`; notification sent to manager with reflection preview

**Flow 4 — Manager Review**
1. Manager team view surfaces "needs attention" (unreviewed 72h+, draft with unlinked, reconciling, stuck commits) separate from "on track"
2. Each IC's card shows Top Rock, tier shape, reflection preview, carry-streak flags
3. Click → drawer with full week, full reflection, commit history with streak chains, single plan-level comment field
4. Manager acknowledges (sets `managerReviewedAt`)
5. No approval gating — review is visibility-only
6. If `managerReviewedAt` null at `reconciledAt + 72h`: dashboard flag + Monday skip-level digest (email via notification-svc); no workflow block

---

## 3. Domain Model

### RCDO Hierarchy (read-only, external)
```
RallyCry (1) ── (N) DefiningObjective (1) ── (N) CoreOutcome (1) ── (N) SupportingOutcome
```
Commits store `supportingOutcomeId` only. Labels hydrated at query time from RCDO service.

### Owned Entities (extend `AbstractAuditingEntity`)

```
WeeklyPlan
  id: UUID
  employeeId: UUID                              (from JWT)
  weekStart: DATE                               (Monday, UTC)
  state: ENUM (DRAFT, LOCKED, RECONCILED, ARCHIVED)
  lockedAt: TIMESTAMPTZ?
  reconciledAt: TIMESTAMPTZ?
  managerReviewedAt: TIMESTAMPTZ?
  reflectionNote: VARCHAR(500)?
  version: BIGINT                               (@Version, optimistic locking)
  UNIQUE(employeeId, weekStart)

WeeklyCommit
  id: UUID
  planId: UUID → WeeklyPlan                     (ON DELETE CASCADE)
  title: VARCHAR(200)
  description: TEXT?
  supportingOutcomeId: UUID                     (FK-style, upstream)
  chessTier: ENUM (ROCK, PEBBLE, SAND)
  categoryTags: TEXT[]
  estimatedHours: NUMERIC(4,1)?
  displayOrder: INT                             (IC-controlled drag order)
  relatedMeeting: VARCHAR(200)?                 (free-text reference; see v2 note)
  carriedForwardFromId: UUID? → WeeklyCommit    (ON DELETE SET NULL)
  carriedForwardToId: UUID? → WeeklyCommit      (ON DELETE SET NULL)
  actualStatus: ENUM (PENDING, DONE, PARTIAL, MISSED)
  actualNote: TEXT?
  INDEX(planId), INDEX(supportingOutcomeId)
  INDEX(planId, chessTier, displayOrder)        (Top Rock lookup)
  INDEX(carriedForwardFromId)                   (carry-streak walk)

ManagerReview
  id: UUID
  planId: UUID → WeeklyPlan                     (ON DELETE CASCADE)
  managerId: UUID
  comment: TEXT?
  acknowledgedAt: TIMESTAMPTZ

NotificationDLT
  id: UUID
  eventType: VARCHAR(50)
  payload: JSONB
  lastError: TEXT
  attempts: INT
  createdAt: TIMESTAMPTZ
  INDEX(createdAt)                              (alerting query)
```

### Derived data (not stored)
- **`topRock`** — first Rock by `displayOrder`, or null
- **`carryStreak`** — chain length via `carriedForwardFromId` walk (capped at 52 hops)
- **`stuckFlag`** — `carryStreak >= 3`

Computed in `DerivedFieldService`, applied uniformly in response mappers.

### Concurrency
`WeeklyPlan` carries `@Version`. Conflicting updates return HTTP 409; UI refetches and retries. `WeeklyCommit` is last-write-wins — the two-tab case is rare for a personal planning tool.

### State Machine
```
         ┌──────────┐   lock     ┌─────────┐   submit      ┌──────────────┐
         │  DRAFT   │──────────▶│ LOCKED  │──────────────▶│  RECONCILED  │
         └──────────┘            └─────────┘                └──────────────┘
              ▲                                                    │
              │ explicit POST /plans                                │  archival
              │                                                     ▼
              │                                              ┌──────────┐
              │                                              │ ARCHIVED │
              │                                              └──────────┘
              │
              └─ carry-forward: new commits in next week's DRAFT
                 (manual, per-item or carry-all, from reconcile view)
```

### Invariants
- `DRAFT`: commits fully mutable; `reflectionNote` not yet editable
- `LOCKED` and `now < weekStart + 4 days`: no mutations allowed
- `LOCKED` and `now >= weekStart + 4 days`: `actualStatus`, `actualNote`, `reflectionNote` mutable (reconciliation mode)
- `RECONCILED`: fully immutable; `ManagerReview` still appendable
- `ARCHIVED`: read-only
- Transitions validated in a Spring state-machine service, never in controllers
- Week math always UTC at service layer; UI converts to employee TZ
- Time-threshold comparisons use application-computed `Instant`, not `NOW()` in SQL
- Carry-streak walk capped at 52 hops

---

## 4. Architecture

### High-level
```
┌────────────────────────────────────────────────────────────────┐
│  PA Host App (existing)                                         │
│  ┌─────────────────────┐      ┌─────────────────────┐          │
│  │  PM remote (existing│      │  WC remote (NEW)    │          │
│  │  reference pattern) │      │  weekly-commit-ui   │          │
│  └─────────────────────┘      └──────────┬──────────┘          │
└────────────────────────────────────────────┼───────────────────┘
                                             │ HTTPS / JWT
                                             ▼
                              ┌────────────────────────────┐
                              │ weekly-commit-service      │
                              │ (Spring Boot 3.3, Java 21) │
                              │  single deployment         │
                              │  ├─ REST controllers        │
                              │  ├─ State machine           │
                              │  ├─ Scheduled jobs          │
                              │  │  (Shedlock-coordinated)  │
                              │  ├─ RCDO client             │
                              │  └─ notification client     │
                              └──────┬────────────┬────────┘
                                     │            │
                             ┌───────▼────┐  ┌────▼────────────────┐
                             │ Postgres16 │  │ notification-svc    │
                             └────────────┘  │ (external)          │
                                             └─────────────────────┘
```

### Why one deployment
At 175 users with ~50 peak concurrent, splitting into api/scheduler/worker is premature. Scheduled jobs run via `@Scheduled` on one randomly-elected pod (Shedlock). Notification send is synchronous with retry + DLT fallback. Horizontal scaling happens on CPU; split by profile only when measured load demands it.

### Why synchronous notification, not outbox
At ~75 notifications/day, the outbox pattern's infrastructure cost (dedicated worker, polling loop, FAILED alarm, replay endpoint) exceeds its benefit. Synchronous call with Resilience4j retry + circuit breaker, backed by a `NotificationDLT` table for durable failure records, is proportionate to scale.

### Monorepo Layout (Yarn Workspaces + Nx)
```
/apps
  /weekly-commit-ui          ← Vite 5 remote, exposes ./WeeklyCommitModule
  /weekly-commit-service     ← Spring Boot 3.3 service
/libs
  /ui-components             ← Flowbite wrappers
  /rtk-api-client            ← typed RTK Query hooks
  /contracts                 ← OpenAPI-generated TS + Java types
```

### Module Federation
- **Host:** PA app; imports `weekly_commit/WeeklyCommitModule`
- **Remote:** `weekly-commit-ui`, single entry `./WeeklyCommitModule`
- Shared singletons: `react`, `react-dom`, `react-router-dom`, `@reduxjs/toolkit`, `@reduxjs/toolkit/query`
- `eager: false` on all shared
- Remote served from S3 + CloudFront at versioned path: `/remotes/weekly-commit/{version}/remoteEntry.js`

### Architectural decisions
- **One service, one deployment.** Scale horizontally on CPU. Split when measured.
- **Synchronous notifications with DLT fallback.** Outbox deferred until volume justifies it.
- **State machine in service code, not DB triggers.** Testable, auditable.
- **Optimistic locking on `WeeklyPlan` only.** Commit-level conflicts aren't worth the complexity at this scale.
- **RCDO consumed, not owned.** Labels fetched fresh; RTK Query caches client-side.
- **Explicit blank state, not side-effect creation.** The IC consciously starting their week is on-brand.
- **No event bus in v1.** Add when a second consumer exists.

---

## 5. API Surface

All endpoints under `/api/v1`. JWT required. Responses follow `{ data, meta }` envelope. Mutations return 409 on `@Version` conflict.

### Plans
```
GET    /plans?employeeId=&weekStart=           → WeeklyPlan (404 if absent)
GET    /plans/me/current                       → WeeklyPlan (404 if absent)
POST   /plans                                  → WeeklyPlan (IC creates current-week DRAFT; idempotent on employeeId+weekStart)
POST   /plans/{id}/transitions                 body: { to: "LOCKED" | "RECONCILED" }
PATCH  /plans/{id}                             body: { reflectionNote? } (reconciliation-mode only; IC-only)
GET    /plans/team?managerId=&weekStart=&page= → Page<WeeklyPlan>
```

### Commits
```
GET    /plans/{planId}/commits                 → WeeklyCommit[]  (each includes carryStreak, stuckFlag)
POST   /plans/{planId}/commits                 → WeeklyCommit
PATCH  /commits/{id}                           → WeeklyCommit (state-aware)
DELETE /commits/{id}                           → 204 (only in DRAFT; nulls carry-forward back-refs)
POST   /commits/{id}/carry-forward             → WeeklyCommit (creates in next week's DRAFT; sets both directional links)
```

### Reviews
```
POST   /plans/{id}/reviews                     → ManagerReview
GET    /plans/{id}/reviews                     → ManagerReview[]
```

### Rollup (manager)
```
GET    /rollup/team?managerId=&weekStart=
  → {
      alignmentPct,
      completionPct,
      tierDistribution,
      unreviewedCount,
      stuckCommitCount,
      members: [
        {
          employeeId,
          name,
          planState,
          topRock,                                 // lowest-displayOrder Rock, or null
          tierCounts,
          reflectionPreview,                       // first ~80 chars
          flags: ["UNREVIEWED_72H" | "DRAFT_WITH_UNLINKED" | "STUCK_COMMIT" | "NO_TOP_ROCK"]
        }
      ],
      byOutcome: [...]
    }
```

### Audit
```
GET    /audit/plans/{planId}                   → AuditEntry[] (MANAGER role or self)
```

### Pagination
- Server-side cap: `size=100`, default `size=20`
- Dataset scale: up to ~50 direct reports per manager

### Null-manager handling
- Rollup queries with `manager_id IS NULL` return empty
- Employees without a manager flagged via admin report

### Rollup cache staleness policy
- Manager-facing `Rollup` tag in RTK Query: `keepUnusedDataFor: 60` + `refetchOnFocus: true`
- Worst-case staleness: ~60 seconds after an IC reconciles

### Performance targets
- `GET /plans/me/current` P95 < 200ms
- `GET /rollup/team` P95 < 500ms for 50-report manager
- `POST /plans/{id}/transitions` P95 < 300ms (includes synchronous notification attempt)

---

## 6. Frontend

### Routes (lazy-loaded, under `/weekly-commit/*`)
```
/weekly-commit/current          IC: current week (blank state or editor; state-aware modes)
/weekly-commit/history          IC: past weeks, read-only
/weekly-commit/team             Manager: team roll-up
/weekly-commit/team/:employeeId Manager: IC deep dive (drawer overlay)
```

On module mount, frontend calls `GET /plans/me/current`. On 404, renders the blank state with explicit "Create plan" action.

### Design principles
- **The interface is discreet.** Urgency through ordering and restraint, not saturation.
- **Flagged items are first, not loud.**
- **Typography does the hierarchy work.** Serif for headers and totals, mono for metadata, sans for body.
- **Every surface shows the minimum information needed for the decision on that surface.** Team view → signal. Drawer → detail.
- **The reflection note is a first-class element**, not a footnote.

### State
- **RTK Query** for all server state; tags: `Plan`, `Commit`, `Review`, `Rollup`, `Audit`
- **Local Redux slice** only for draft commit form and optimistic reordering
- State transitions invalidate `Plan` + `Commit` tags for that planId
- **Rollup staleness:** 60s TTL + refetch-on-focus
- **409 conflict handling:** global RTK Query middleware shows toast + triggers refetch
- RCDO data via RTK Query with `keepUnusedDataFor: 600`

### Key Components
- `<WeekEditor />` — state-aware: blank state, DRAFT, LOCKED pre-day-4 read-only, LOCKED post-day-4 reconciliation mode
- `<RCDOPicker />` — typeahead with 4-level breadcrumb
- `<ChessTier />` — Rock / Pebble / Sand as vertical spine, not a badge
- `<ReconcileTable />` — planned (frozen) vs. actual (editable), keyboard-first
- `<ReflectionField />` — 500-char plain-text area; char count; no markdown
- `<CarryForwardRow />` — per-commit button + "carry all missed/partial"
- `<CarryStreakBadge />` — inline on commits with streak ≥2; flagged at ≥3
- `<TeamRollup />` — ordered list: flagged first, reviewed below
- `<MemberCard />` — name, Top Rock, tier shape, reflection preview, flag markers
- `<IcDrawer />` — overlay for manager IC deep-dive; preserves team context
- `<StateBadge />` — current plan state with next-action hint
- `<ConflictToast />` — 409 handler UI

### Stack
- **React 18** (host-pinned; imported from host via Module Federation shared singleton)
- **TypeScript 5.x strict mode** (`"strict": true`, `noImplicitAny`, `strictNullChecks`, `noUncheckedIndexedAccess`)
- **Vite 5** with Module Federation
- **Redux Toolkit** + **RTK Query** for all server state (cache invalidation via tags)
- **Tailwind CSS** + **Flowbite React** for UI primitives

### Quality
- **ESLint 9** with `@typescript-eslint` and `eslint-plugin-react`; zero warnings policy in CI
- **Prettier 3.3** with project-standard config; `prettier --check` as PR gate
- No `any` without an adjacent `// eslint-disable-next-line` justification comment

### Styling
- Tailwind + Flowbite React
- **Day-1 spike:** verify host's `@host/design-system` tokens override Flowbite defaults. Fallback: Headless UI + Tailwind.
- No custom CSS files; utility classes only

### Testing
- **Vitest** for component logic, hooks, reducers (target 80%)
- **Playwright** for remote-in-isolation smoke tests
- **Cypress + Cucumber/Gherkin** for cross-remote E2E in the host context; one `.feature` per core flow, BDD syntax per brief

---

## 7. Backend

### Stack
- Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Scheduling)
- PostgreSQL 16.4, Hibernate 6.x, HikariCP
- Flyway for all schema (no `ddl-auto`)
- Spring Authorization Server client config for Auth0 JWT validation
- MapStruct for DTO ↔ entity
- Resilience4j for outbound client resilience (RCDO, notification-svc)
- Shedlock for scheduled-job leader election

### Package layout
```
com.acme.weeklycommit
  ├─ api/          controllers, DTOs, exception handlers, 409 conflict mapper
  ├─ domain/       entities, enums, value objects
  ├─ repo/         Spring Data repositories
  ├─ service/      business logic, state machine, DerivedFieldService, notification sender
  ├─ scheduled/    archival, auto-lock, unreviewed-72h digest
  ├─ integration/  rcdo client, notification client
  └─ config/       security, webclient
```

### State machine
Single `@Service WeeklyPlanStateMachine` with transition table and guards. Transitions are `@Transactional`. Notification send is synchronous after transaction commit; failure writes to `NotificationDLT` table. Idempotent via `plan_id + target_state + version`.

### DerivedFieldService
Pure functions computing `topRock`, `carryStreak`, `stuckFlag`. No mutation. Carry-streak walk capped at 52 hops. Applied in response mappers. Tested with happy-path chains and the 52-hop cap boundary.

### Notification sending
- Synchronous REST call to notification-svc after transaction commits
- Resilience4j: 3 retries with exponential backoff, circuit breaker on notification-svc outages
- On permanent failure: write `NotificationDLT` row with payload and error
- CloudWatch alarm on any DLT row < 1h old
- Admin endpoint `POST /admin/notifications/dlt/{id}/replay` requeues manually

### Scheduled jobs (Shedlock-coordinated)
- **Auto-lock** — hourly scan: any `DRAFT` plan past cutoff → `LOCKED` + notification
- **Archival** — nightly: `RECONCILED` plans older than 90 days → `ARCHIVED`
- **Unreviewed digest** — Monday 09:00 UTC: scan `state=RECONCILED AND managerReviewedAt IS NULL AND reconciledAt < :threshold`; send digest to skip-level via notification-svc
- Threshold comparisons use application-computed `Instant`

### Migrations (Flyway)
```
V1__create_weekly_plan.sql                    (includes reflectionNote)
V2__create_weekly_commit.sql
V3__create_manager_review.sql
V4__create_notification_dlt.sql
V5__create_audit_log.sql
V6__indexes_and_constraints.sql
  - composite index: Top Rock lookup (planId, chessTier, displayOrder)
  - index: carry-streak walk (carriedForwardFromId)
  - standard indexes on foreign keys and query filters
```

### Testing
- **Unit:** Mockito for services, `@DataJpaTest` for repositories
- **Integration:** `@SpringBootTest` + Testcontainers (Postgres 16); covers state machine, DLT path, scheduled jobs, derived fields
- **Contract:** generated from OpenAPI; WireMock for RCDO, notification-svc
- **Coverage:** JaCoCo 80% overall (brief requirement); exclude generated code, DTOs, config
- **Mutation testing:** PITest on `WeeklyPlanStateMachine` specifically, nightly CI
- **Quality:** Spotless (google-java-format), SpotBugs with project exclusions

---

## 8. Integrations

### RCDO Service (read-only consumer)
- Spring `WebClient`, retry 3x with exp backoff, Resilience4j circuit breaker
- Endpoints:
  - `GET /rcdo/supporting-outcomes?orgId=&active=true`
  - `GET /rcdo/supporting-outcomes/{id}?hydrate=full`
- No backend cache in v1; RTK Query handles client-side freshness
- **Day-1 spike:** confirm endpoint contract (A1)

### notification-svc (internal consumer)
- Existing shared service consumed via REST
- Called synchronously from state-machine service, after transaction commit
- Resilience4j circuit breaker; retries exhausted → `NotificationDLT`
- **Day-1 spike:** confirm API contract (A6). If absent, ~1 week scope addition.

### Auth0
- OAuth2 resource server in Spring Security
- JWT claims: `sub`, `org_id`, `manager_id` (nullable), `roles`, `timezone` (IANA format)
- Manager-scope endpoints require `roles` contains `MANAGER`
- `manager_id` staleness: changes propagate on JWT refresh (≤15min acceptable)

---

## 9. Infra & Deploy

### AWS
- **EKS:** one deployment for `weekly-commit-service`; HPA on CPU 70%, min 2 / max 6
- **CloudFront + S3:** static remote bundle; `max-age=31536000, immutable` on versioned paths, `no-cache` on manifest
- **RDS Postgres 16.4:** Multi-AZ, 20GB start, gp3
- **CloudWatch:** metrics, logs, alarms

### CI/CD (GitHub Actions)
- Frontend: lint → typecheck → vitest → build → upload to S3 → invalidate CF path
- Backend: spotless → build → test → JaCoCo gate → build image → push ECR → argo sync
- PR gates: above + Cypress smoke against ephemeral env
- Version stamping: git SHA in remote manifest; backend exposes `/actuator/info`
- Mutation tests nightly, non-blocking

### Observability
- Spring Boot Actuator + Micrometer → CloudWatch
- Frontend: Sentry (errors) + RUM ping on route enter
- Audit log (`audit_log` table): state transitions + manager review events
- Audit log access: `/api/v1/audit/plans/{id}` (MANAGER or self); 2y retention
- **Audit + GDPR policy requires legal sign-off** before Phase 2
- Scheduled-job observability: success/failure counters; alarm on 2+ consecutive failures
- **DLT alarm:** CloudWatch fires on any `NotificationDLT` row < 1h old; paged to on-call

### Scale targets
- Peak concurrency: 50 users (Monday AM / Friday PM)
- Load test target: 100 concurrent users with 2x safety margin
- Data volume: ~18k plans + ~180k commits across 2y

---

## 10. Rollout Plan

| Phase | Scope | Gate |
|---|---|---|
| 0 | Internal dogfood: 1 team (8–12 people) | Zero-defect week; notifications deliver 100% |
| 1 | Eng + Product orgs (~40 people) | <1% error rate, p95 targets met |
| 2 | Full org (175+) | 15-Five write-path disabled; legal sign-off on audit retention |
| 3 | 15-Five decommissioned | Historical export archived to S3 |

Kill switch: feature flag at host app level to fall back to 15-Five link. Removed at Phase 3.

---

## 11. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| RCDO service API differs from assumption | M | H | Day-1 spike; client behind interface |
| notification-svc missing or incompatible | M | H | Day-1 spike; ~1 week scope if missing |
| Module Federation version drift with host | L | H | Pin shared versions; weekly smoke test against host main |
| State machine edge cases on TZ / DST boundaries | M | M | All week math UTC; unit tests across DST transitions |
| Managers ignore review flow | M | H | Dashboard flag + skip-level digest at 72h (non-blocking) |
| Actual max direct reports >> 50 | L | M | Re-scope rollup query; defer to v1.1 if encountered |
| Notification-svc extended outage | L | M | Circuit breaker + DLT; admin replay; alarm on < 1h DLT rows |
| 1:1 RCDO linking proves insufficient | M | M | Data model extensible to M:N with junction table in v2 |
| Top Rock selection by `displayOrder` mismatches intent | M | L | Alternative rules backward-compatible (see open question) |
| Reflection note privacy concerns from ICs | M | M | Clear UI label of visibility scope; IC can leave blank |
| Legal rejects audit retention policy | L | M | Gate Phase 2 on sign-off; fallback is 90-day full deletion |
| Null-manager employees | L | L | Queries return empty; admin report surfaces gap |
| "Top Rock" naming collides with Rock.so | L | L | Internal-only UI; revisit if Rock.so integration ever relevant |

---

## 12. Open Questions for Stakeholders

1. **RCDO source of truth** — confirm existing service + endpoint contract (A1)
2. **Chess layer semantics** — confirm Rocks/Pebbles/Sand; quota per tier enforced? (A2)
3. **PA/PM remote pattern** — obtain existing PM remote config (A3)
4. **Week boundary policy** — Monday–Sunday confirmed? Auto-lock time? (A4)
5. **notification-svc** — confirm existence, obtain contract (A6)
6. **Max direct reports** — largest real team size? (A7)
7. **Auto-lock behavior** — what time, and does it fire on empty plans?
8. **Historical data import from 15-Five** — non-goal confirmed?
9. **RCDO linkage cardinality** — 1:1 sufficient, or primary + secondary? (A8)
10. **Audit log access + retention** — confirm MANAGER + self; legal sign-off on retention
11. **Top Rock selection rule** — IC-controlled via `displayOrder`, or rule-based? (A9)
12. **Null-manager employees** — every employee has a manager? (A10)
13. **Reflection note visibility** — confirm manager + digest scope; confirm IC can leave blank
14. **Carry-streak threshold** — 3 weeks correct, or configurable?
15. **[brief deviation] Outlook Graph integration deferral** — brief names it as required tech. Confirm v2 deferral with free-text `relatedMeeting` stand-in in v1, or restore to v1 scope (adds ~1–2 weeks).
16. **[brief deviation] SQS/SNS deferral** — brief names these as required cloud tech. Confirm v2 deferral with synchronous send + DLT in v1, or restore to v1 scope (adds outbox pattern, worker deployment, ~1–1.5 weeks).
17. **[brief deviation] `RECONCILING` state deferral** — brief writes lifecycle as `DRAFT → LOCKED → RECONCILING → RECONCILED → Carry Forward`. Confirm the four-state simplification, or restore `RECONCILING` (adds a transition, an event, and an audit entry with no behavior change from v6's approach).
18. **Analytics dashboard deferral** — v2 candidate; confirm not required for v1 ship.

---

## 13. Definition of Done (v1)

- [ ] All four §2 flows implemented end-to-end
- [ ] State machine transitions covered by unit + integration tests
- [ ] State machine mutation-tested (PITest ≥ 70% mutation score on `WeeklyPlanStateMachine`)
- [ ] Notification DLT verified: injected failures land in DLT and trigger alarm
- [ ] Scheduled jobs (auto-lock, archival, unreviewed digest) covered by integration tests
- [ ] Optimistic locking on `WeeklyPlan` verified: concurrent PATCH returns 409, UI recovers
- [ ] DerivedFieldService verified: Top Rock, carry-streak, stuck flag; cap-hit covered
- [ ] Reflection note: IC writes at reconcile, surfaces in manager card preview, full text in drawer
- [ ] Carry-streak: badge on commits ≥2 weeks, flagged at ≥3, visible to manager
- [ ] P95 latency targets met under load test (100 concurrent users, 50k plans)
- [ ] JaCoCo ≥ 80% overall; Vitest ≥ 80%; Cypress covers each core flow
- [ ] Remote renders standalone AND inside PA host
- [ ] Auth0 RBAC enforced on manager endpoints
- [ ] Flyway migrations run clean from empty DB
- [ ] Manager team view: flagged-first ordering; reflection previews and carry-streak flags visible
- [ ] Top Rock surfaces correctly; "no Top Rock" surfaces as a flag
- [ ] Null-manager rollup queries return empty; admin report lists unassigned employees
- [ ] Runbook: state-machine recovery, DLT replay, scheduled-job re-run, remote rollback, legal escalation
- [ ] Accessibility: WCAG 2.1 AA on commit editor, reconcile table, manager team view
- [ ] Day-1 spikes resolved: RCDO contract, notification-svc contract, Flowbite tokens
- [ ] Legal sign-off on audit retention policy (gates Phase 2)
- [ ] v2 candidates documented and estimated: analytics dashboard, Outlook Graph, outbox/SQS
- [ ] Brief-deviation deferrals ratified by stakeholders (§12 Q15–17) before kickoff

---

## Appendix A — Brief Coverage Audit

Mapping of every technical requirement from the original brief to its treatment in this doc.

### Languages — all required, all v1
| Brief requirement | Treatment |
|---|---|
| TypeScript (strict mode) | §6 Stack, explicit `strict: true` + `noUncheckedIndexedAccess` |
| Java 21 | §7 Stack |
| SQL | §3 (data model), §7 (Flyway migrations) |

### Development Tools — all required, all v1
| Brief requirement | Treatment |
|---|---|
| React 18 | §6 Stack (host-pinned via Module Federation) |
| Vite 5 with Module Federation | §4, §6 |
| Spring Boot 3.3 | §4, §7 |
| Redux Toolkit with RTK Query | §6 |
| Flowbite React | §6 |
| Tailwind CSS | §6 |
| Vitest | §6, §7 |
| Playwright | §6 |

### Cloud Platforms
| Brief requirement | Treatment |
|---|---|
| AWS EKS | §9 |
| CloudFront CDN | §9 |
| S3 | §9 |
| SQS/SNS | **[v2 — brief deviation]** deferred per §1, requires §12 Q16 ratification |

### Other Requirements
| Brief requirement | Treatment |
|---|---|
| PostgreSQL 16.4 | §7 |
| Hibernate/JPA with Spring Data | §7 |
| Flyway migrations | §7 |
| Auth0 (OAuth2 JWT) | §8 |
| Yarn Workspaces + Nx monorepo | §4 |
| Micro-frontend architecture (Vite Module Federation host/remote) | §4 |
| Outlook Graph API integration | **[v2 — brief deviation]** deferred per §1, requires §12 Q15 ratification; v1 ships free-text `relatedMeeting` |

### Functional Requirements
| Brief requirement | Treatment |
|---|---|
| Weekly commit CRUD with RCDO hierarchy linking | §2, §5 |
| Chess layer for categorization and prioritization | §3, glossary |
| Full weekly lifecycle state machine `DRAFT → LOCKED → RECONCILING → RECONCILED → Carry Forward` | **[partial — brief deviation]** v6 ships `DRAFT → LOCKED → RECONCILED` (RECONCILING collapsed into a UI mode); carry-forward preserved. Requires §12 Q17 ratification |
| Reconciliation view comparing planned vs. actual | §6 (`<ReconcileTable />`) |
| Manager dashboard with team roll-up | §2 Flow 4, §5 rollup, §6 `<TeamRollup />` |
| Micro-frontend integration into existing PA host app following PM remote pattern | §4 |

### Performance Benchmarks — all v1
| Brief requirement | Treatment |
|---|---|
| API response times under 200ms for plan retrieval | §5 (P95 < 200ms) |
| Lazy-loaded routes for sub-second initial render | §6 routes |
| Module Federation remote bundle size optimized for CDN delivery | §4 CloudFront versioned path + long cache |
| Pagination support (Spring Data Pageable) for team views up to 2000 records | §5 |

### Code Quality Standards — all v1
| Brief requirement | Treatment |
|---|---|
| TypeScript strict mode | §6 Stack |
| JaCoCo 80% minimum backend coverage | §7 |
| Vitest unit tests for all components | §6 Testing |
| Cypress E2E with Cucumber/Gherkin BDD syntax | §6 Testing |
| ESLint 9 + Prettier 3.3 (frontend) | §6 Quality |
| Spotless + SpotBugs (backend) | §7 |
| All entities extend `AbstractAuditingEntity` | §3 |
| RTK Query for all API calls with cache invalidation | §6 State |

### Summary

**In v1 as required:** all languages, all dev tools, all cloud platforms except SQS/SNS, all other requirements except Outlook Graph, all functional requirements except the RECONCILING state, all performance benchmarks, all code quality standards.

**Deferred pending ratification:** three brief items (Outlook Graph, SQS/SNS, RECONCILING state) are deferred with architectural rationale in §1 and stakeholder questions in §12. No requirement is silently cut.
