# Tech Stack — Weekly Commit Module

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│  PA Host App (existing)                                         │
│  ┌─────────────────────┐      ┌─────────────────────┐          │
│  │  PM remote          │      │  WC remote (NEW)    │          │
│  │  (reference pattern)│      │  weekly-commit-ui   │          │
│  └─────────────────────┘      └──────────┬──────────┘          │
└────────────────────────────────────────────┼───────────────────┘
                                             │ HTTPS / Auth0 JWT
                                             ▼
                              ┌────────────────────────────┐
                              │ weekly-commit-service      │
                              │ (Spring Boot 3.3, Java 21) │
                              │  single deployment         │
                              │  ├─ REST controllers       │
                              │  ├─ State machine          │
                              │  ├─ Scheduled jobs         │
                              │  │  (Shedlock-coordinated) │
                              │  ├─ RCDO client            │
                              │  └─ notification client    │
                              └──────┬────────────┬────────┘
                                     │            │
                             ┌───────▼────┐  ┌────▼────────────────┐
                             │ Postgres16 │  │ notification-svc    │
                             │ (RDS MAZ)  │  │ (external, REST)    │
                             └────────────┘  └─────────────────────┘
                                     ▲
                                     │ REST (read-only)
                             ┌───────┴─────────┐
                             │ RCDO Service    │
                             │ (external)      │
                             └─────────────────┘

Remote bundle: CloudFront + S3
  /remotes/weekly-commit/{version}/remoteEntry.js
  max-age=31536000, immutable on versioned paths
  no-cache on manifest
```

## Stack Decisions

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| Backend language | Java | 21 (LTS) | Brief requirement; current LTS; virtual threads available |
| Backend framework | Spring Boot | 3.3 | Brief requirement; Web + Data JPA + Security + Validation + Scheduling |
| ORM | Hibernate | 6.x | Bundled with Spring Data JPA 3.3 |
| DB connection pool | HikariCP | Spring default | Spring Boot 3 default; zero extra config |
| Database | PostgreSQL | 16.4 | Brief requirement; MultiAZ RDS |
| Migrations | Flyway | 10.x | Brief requirement; versioned SQL, no `ddl-auto` |
| Auth | Auth0 (OAuth2 JWT) | — | Brief requirement; Spring Authorization Server resource-server config |
| DTO mapping | MapStruct | 1.5.x | Compile-time mappers; faster than reflection-based alternatives |
| Resilience | Resilience4j | 2.x | Retries + circuit breaker for RCDO + notification-svc |
| Scheduled job leader election | Shedlock | 5.x | Prevents duplicate runs across pods |
| Frontend language | TypeScript | 5.x strict | Brief requirement; `strict: true`, `noImplicitAny`, `strictNullChecks`, `noUncheckedIndexedAccess` |
| Frontend framework | React | 18 | Brief requirement; host-pinned via Module Federation shared singleton |
| Build tool | Vite | 5 | Brief requirement; Module Federation via `@originjs/vite-plugin-federation` |
| State management | Redux Toolkit + RTK Query | current | Brief requirement; tag-based cache invalidation |
| UI primitives | Flowbite React | current | Brief requirement |
| CSS | Tailwind CSS | 3.x | Brief requirement; utility classes only, no custom CSS |
| Frontend lint | ESLint | 9 | Zero-warnings CI gate |
| Formatter | Prettier | 3.3 | `prettier --check` as PR gate |
| Backend lint | Spotless (google-java-format) + SpotBugs | current | With project-level SpotBugs exclusions |
| Unit test (FE) | Vitest | current | Brief requirement; 80% target |
| Unit test (BE) | JUnit 5 + Mockito | current | Spring Boot default |
| Integration test (BE) | Testcontainers (Postgres 16) | current | Real DB in CI |
| Coverage (BE) | JaCoCo | current | 80% floor; excludes generated + DTO + config |
| Mutation test (BE) | PITest | current | Nightly on `WeeklyPlanStateMachine`, ≥70% mutation score |
| Contract test | WireMock | current | Stubs RCDO + notification-svc |
| Component/smoke (FE) | Playwright | current | Brief requirement; remote in isolation |
| Cross-remote E2E | Cypress + Cucumber/Gherkin | current | Brief requirement; BDD `.feature` per core flow in host context |
| Monorepo | Yarn Workspaces + Nx | current | Brief requirement |
| Compute | AWS EKS | — | Brief requirement; HPA on CPU 70%, min 2 / max 6 |
| Static hosting | AWS S3 + CloudFront | — | Brief requirement; versioned remote path |
| DB hosting | AWS RDS Postgres 16.4 | — | Multi-AZ, 20 GB start, gp3 |
| Metrics / logs | Spring Actuator + Micrometer → CloudWatch | current | Alarm on 2+ consecutive scheduled-job failures; DLT < 1h alarm |
| FE error tracking | Sentry + RUM | current | Errors + route-enter ping |
| CI/CD | GitHub Actions + ArgoCD | — | Lint → typecheck → test → coverage gate → build → ECR → argo sync |

## Key Dependencies

### Backend (`apps/weekly-commit-service`, Maven/Gradle)

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-security`
- `org.springframework.boot:spring-boot-starter-oauth2-resource-server` — Auth0 JWT validation
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.postgresql:postgresql` — JDBC driver
- `org.flywaydb:flyway-core` + `flyway-database-postgresql`
- `org.mapstruct:mapstruct` + `mapstruct-processor`
- `io.github.resilience4j:resilience4j-spring-boot3` — retries + circuit breaker
- `net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template`
- `io.micrometer:micrometer-registry-cloudwatch2`
- Test: `spring-boot-starter-test`, `org.testcontainers:postgresql`, `com.github.tomakehurst:wiremock-jre8-standalone`, `org.pitest:pitest-maven`
- Quality: `com.diffplug.spotless:spotless-maven-plugin` (google-java-format config), `com.github.spotbugs:spotbugs-maven-plugin`, `org.jacoco:jacoco-maven-plugin`

### Frontend (`apps/weekly-commit-ui`, Yarn)

- `react` (host-pinned via Module Federation)
- `react-dom` (host-pinned)
- `react-router-dom` (host-pinned)
- `@reduxjs/toolkit` (host-pinned)
- `@reduxjs/toolkit/query` (host-pinned)
- `@originjs/vite-plugin-federation` — Module Federation for Vite
- `vite` 5, `@vitejs/plugin-react`
- `typescript` 5.x
- `tailwindcss`, `autoprefixer`, `postcss`
- `flowbite-react`, `flowbite`
- `@sentry/react`
- Test: `vitest`, `@vitest/coverage-v8`, `@testing-library/react`, `@playwright/test`, `cypress`, `@badeball/cypress-cucumber-preprocessor`
- Quality: `eslint` 9, `@typescript-eslint/*`, `eslint-plugin-react`, `eslint-plugin-react-hooks`, `prettier` 3.3

### Shared libs (`libs/`)

- `libs/ui-components` — Flowbite wrappers
- `libs/rtk-api-client` — typed RTK Query hooks
- `libs/contracts` — OpenAPI-generated TS types (`openapi-typescript`) + Java types (`openapi-generator-maven-plugin`)

## Environment Variables

Template lives at [`.env`](../.env). All values empty by default; filled per environment.

**Backend (`weekly-commit-service`)**

```bash
# --- Datasource ---
SPRING_DATASOURCE_URL=           # jdbc:postgresql://host:5432/weeklycommit
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=

# --- Auth0 ---
AUTH0_ISSUER_URI=                # https://<tenant>.auth0.com/
AUTH0_AUDIENCE=                  # API audience for this service

# --- Upstream services ---
RCDO_BASE_URL=                   # https://rcdo.internal
RCDO_TIMEOUT_MS=2000
RCDO_SERVICE_TOKEN=              # Bearer token for service-to-service auth (ADR-0001). Empty in dev/test; required in prod.
NOTIFICATION_SVC_BASE_URL=       # https://notification-svc.internal
NOTIFICATION_SVC_TIMEOUT_MS=3000
NOTIFICATION_SVC_SERVICE_TOKEN=  # Bearer token for service-to-service auth (ADR-0002). Same sourcing as RCDO_SERVICE_TOKEN.

# --- Resilience4j (optional overrides; defaults in application.yml) ---
RESILIENCE4J_NOTIFICATION_MAX_ATTEMPTS=3
RESILIENCE4J_NOTIFICATION_WAIT_MS=500

# --- Shedlock ---
SHEDLOCK_ENABLED=true

# --- Scheduled jobs ---
AUTO_LOCK_CRON=0 0 * * * *       # hourly on the hour
ARCHIVAL_CRON=0 0 2 * * *        # 02:00 UTC nightly
UNREVIEWED_DIGEST_CRON=0 0 9 * * MON  # Monday 09:00 UTC
UNREVIEWED_THRESHOLD_HOURS=72
ARCHIVAL_OLDER_THAN_DAYS=90
AUTO_LOCK_CUTOFF_HOURS_AFTER_WEEK_START=36  # Monday 12:00 UTC for Monday-start week

# --- Observability ---
MICROMETER_CLOUDWATCH_NAMESPACE=WeeklyCommit
AUDIT_RETENTION_DAYS=730

# --- Admin replay ---
ADMIN_REPLAY_ENABLED=true
```

**Frontend (`weekly-commit-ui`, injected at build time via Vite)**

```bash
VITE_API_BASE_URL=               # https://api.internal/api/v1
VITE_REMOTE_NAME=weekly_commit
VITE_REMOTE_VERSION=             # git SHA, set in CI
VITE_SENTRY_DSN=
VITE_FEATURE_FLAG_KILL_SWITCH=false  # host-app level in prod; local toggle in dev
GIT_SHA=                          # consumed by vite.config.ts -> __WC_GIT_SHA__ define; CI sets, local falls back to 'dev'
```

`GIT_SHA` is intentionally not `VITE_`-prefixed: it's read in node context
inside `vite.config.ts` and substituted into the bundle via `define`, not
exposed via `import.meta.env`. `VITE_REMOTE_VERSION` is the runtime mirror
the host uses to pick a remoteEntry path; both come from the same commit
SHA in CI.

**Host integration (PA host app consumes)**

```bash
# Values host needs to resolve the remote
VITE_WC_REMOTE_ENTRY=            # https://cdn.internal/remotes/weekly-commit/{version}/remoteEntry.js
```

**CI secrets (GitHub Actions, not checked in)**

```
AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_REGION
ECR_REGISTRY
ARGOCD_AUTH_TOKEN
SENTRY_AUTH_TOKEN
```

## Database Schema

All owned entities extend `AbstractAuditingEntity` (createdBy / createdDate / lastModifiedBy / lastModifiedDate). Schema applied via Flyway migrations V1–V6.

### `weekly_plan` (V1)

```sql
CREATE TABLE weekly_plan (
    id                     UUID PRIMARY KEY,
    employee_id            UUID NOT NULL,
    week_start             DATE NOT NULL,                   -- Monday, UTC
    state                  VARCHAR(16) NOT NULL,            -- DRAFT | LOCKED | RECONCILED | ARCHIVED
    locked_at              TIMESTAMPTZ,
    reconciled_at          TIMESTAMPTZ,
    manager_reviewed_at    TIMESTAMPTZ,
    reflection_note        VARCHAR(500),
    version                BIGINT NOT NULL DEFAULT 0,       -- @Version
    created_by             VARCHAR(64) NOT NULL,
    created_date           TIMESTAMPTZ NOT NULL,
    last_modified_by       VARCHAR(64) NOT NULL,
    last_modified_date     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_weekly_plan_employee_week UNIQUE (employee_id, week_start),
    CONSTRAINT ck_weekly_plan_state CHECK (state IN ('DRAFT','LOCKED','RECONCILED','ARCHIVED'))
);
CREATE INDEX idx_weekly_plan_employee ON weekly_plan(employee_id);
CREATE INDEX idx_weekly_plan_state ON weekly_plan(state);
```

### `weekly_commit` (V2)

```sql
CREATE TABLE weekly_commit (
    id                       UUID PRIMARY KEY,
    plan_id                  UUID NOT NULL REFERENCES weekly_plan(id) ON DELETE CASCADE,
    title                    VARCHAR(200) NOT NULL,
    description              TEXT,
    supporting_outcome_id    UUID NOT NULL,                 -- FK-style, upstream RCDO
    chess_tier               VARCHAR(8) NOT NULL,           -- ROCK | PEBBLE | SAND
    category_tags            TEXT[] NOT NULL DEFAULT '{}',
    estimated_hours          NUMERIC(4,1),
    display_order            INT NOT NULL,
    related_meeting          VARCHAR(200),                  -- free-text; v2 = Outlook Graph
    carried_forward_from_id  UUID REFERENCES weekly_commit(id) ON DELETE SET NULL,
    carried_forward_to_id    UUID REFERENCES weekly_commit(id) ON DELETE SET NULL,
    actual_status            VARCHAR(8) NOT NULL DEFAULT 'PENDING',  -- PENDING | DONE | PARTIAL | MISSED
    actual_note              TEXT,
    created_by               VARCHAR(64) NOT NULL,
    created_date             TIMESTAMPTZ NOT NULL,
    last_modified_by         VARCHAR(64) NOT NULL,
    last_modified_date       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_weekly_commit_tier   CHECK (chess_tier IN ('ROCK','PEBBLE','SAND')),
    CONSTRAINT ck_weekly_commit_status CHECK (actual_status IN ('PENDING','DONE','PARTIAL','MISSED'))
);
```

### `manager_review` (V3)

```sql
CREATE TABLE manager_review (
    id                 UUID PRIMARY KEY,
    plan_id            UUID NOT NULL REFERENCES weekly_plan(id) ON DELETE CASCADE,
    manager_id         UUID NOT NULL,
    comment            TEXT,
    acknowledged_at    TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(64) NOT NULL,
    created_date       TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(64) NOT NULL,
    last_modified_date TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_manager_review_plan ON manager_review(plan_id);
```

### `notification_dlt` (V4)

```sql
CREATE TABLE notification_dlt (
    id           UUID PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,
    payload      JSONB NOT NULL,
    last_error   TEXT NOT NULL,
    attempts     INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_dlt_created ON notification_dlt(created_at);
```

### `audit_log` (V5)

```sql
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY,
    entity_type    VARCHAR(32) NOT NULL,            -- WEEKLY_PLAN | MANAGER_REVIEW
    entity_id      UUID NOT NULL,
    event_type     VARCHAR(32) NOT NULL,            -- STATE_TRANSITION | MANAGER_REVIEW
    actor_id       UUID,
    from_state     VARCHAR(16),
    to_state       VARCHAR(16),
    metadata       JSONB,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id, occurred_at DESC);
```

### Indexes & Constraints (V6)

```sql
-- Top Rock lookup (lowest displayOrder Rock per plan)
CREATE INDEX idx_weekly_commit_toprock ON weekly_commit(plan_id, chess_tier, display_order);

-- Carry-streak walk
CREATE INDEX idx_weekly_commit_carry ON weekly_commit(carried_forward_from_id);

-- Supporting Outcome analytics
CREATE INDEX idx_weekly_commit_outcome ON weekly_commit(supporting_outcome_id);

-- Plan lookup
CREATE INDEX idx_weekly_commit_plan ON weekly_commit(plan_id);

-- Shedlock table (created from shedlock schema)
CREATE TABLE shedlock (
    name        VARCHAR(64)  PRIMARY KEY,
    lock_until  TIMESTAMPTZ  NOT NULL,
    locked_at   TIMESTAMPTZ  NOT NULL,
    locked_by   VARCHAR(255) NOT NULL
);
```

## API Endpoints Summary

All endpoints under `/api/v1`; JWT required on every call. Responses use `{ data, meta }` envelope. Mutations return HTTP 409 on `@Version` conflict.

| Method | Path | Purpose | Role |
|---|---|---|---|
| GET | `/plans?employeeId=&weekStart=` | Specific plan lookup | self or MANAGER |
| GET | `/plans/me/current` | Current-week plan for caller (404 if absent) | self |
| POST | `/plans` | Create current-week DRAFT; idempotent on `(employeeId, weekStart)` | self |
| POST | `/plans/{id}/transitions` | Body `{ to: "LOCKED" \| "RECONCILED" }` | self |
| PATCH | `/plans/{id}` | Body `{ reflectionNote? }` (reconciliation-mode only) | self |
| GET | `/plans/team?managerId=&weekStart=&page=` | Paged team plans | MANAGER |
| GET | `/plans/{planId}/commits` | Commits for a plan (with derived `carryStreak`, `stuckFlag`) | self or MANAGER |
| POST | `/plans/{planId}/commits` | Create commit (DRAFT only) | self |
| PATCH | `/commits/{id}` | State-aware mutation | self |
| DELETE | `/commits/{id}` | Delete (DRAFT only; nulls carry-forward back-refs) | self |
| POST | `/commits/{id}/carry-forward` | Creates twin in next week's DRAFT | self |
| POST | `/plans/{id}/reviews` | Manager ack + optional comment | MANAGER |
| GET | `/plans/{id}/reviews` | List reviews on plan | self or MANAGER |
| GET | `/rollup/team?managerId=&weekStart=` | Team roll-up with flags, Top Rock, reflection preview | MANAGER |
| GET | `/audit/plans/{planId}` | Audit entries for plan | self or MANAGER |
| POST | `/admin/notifications/dlt/{id}/replay` | Requeue failed notification | ADMIN |

Pagination: `size` default 20, cap 100.
