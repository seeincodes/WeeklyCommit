# Error & Fix Log — Weekly Commit Module

This file is the durable record of non-trivial errors encountered during development and how we fixed them. Log everything that took > 5 minutes to diagnose, anything that could recur, and anything a teammate would want to know the next time they hit the same stack trace.

**Do log:** build failures, runtime errors in dev/staging/prod, API contract errors (RCDO, notification-svc, Auth0), DB errors (Flyway, JPA, constraint violations), deployment errors, Module Federation runtime errors, state-machine transition failures, Shedlock contention, DLT accumulation incidents.

**Don't log:** typos, linter warnings that you fixed in the same commit, test failures that reflect a bug you're actively fixing (the test itself is the record), expected validation errors in unit tests.

## Template

Copy this block when adding a new entry. Put new entries at the top of the `## Log` section (reverse chronological).

```
### YYYY-MM-DD — <one-line title>

**Error**
<exact error message / stack trace snippet / screenshot reference>

**Context**
<what you were doing, which app/service, which branch, which env>

**Root Cause**
<what actually caused it — be specific; "the ORM" isn't a root cause, "Hibernate insert-order with ON CASCADE causes FK violation on carriedForwardFromId in same tx" is>

**Fix**
<the change made, file paths if helpful>

**Prevention**
<what would have caught this earlier — new test, lint rule, docs update, monitoring alarm>
```

## Log

### 2026-04-24 — `./mvnw` fails with broken `$JAVA_HOME`

**Error**
```
./mvnw: line 18: /Library/Java/JavaVirtualMachines/jdk-22.jdk/bin/java: No such file or directory
```

**Context**
First `./mvnw verify` run in a fresh shell on macOS. `JAVA_HOME` was
exported to a JDK path that no longer existed on disk (stale config from a
prior Java install).

**Root Cause**
The wrapper script authored in group 3 trusted `${JAVA_HOME:-/usr}/bin/java`
blindly — `:-` only falls back if `$JAVA_HOME` is *unset*, not if the path
it points at is broken. So the wrapper tried to exec a non-existent binary.

**Fix**
Rewrote [`apps/weekly-commit-service/mvnw`](../apps/weekly-commit-service/mvnw)
with a resolution order: (1) `$JAVA_HOME/bin/java` only when it's actually
executable, (2) on macOS `/usr/libexec/java_home -v 21` (the project's
target), then any JDK `java_home` can find, (3) `java` on `PATH`. Prints a
clear error if none work.

**Prevention**
- Regression vector is "someone rewrites mvnw and drops the fallback
  logic." Keep the `JAVA_CMD` resolution as-is.
- `.tool-versions` pins `java temurin-21.0.5+11.0.LTS`; asdf / mise will
  auto-install on first `cd` into the repo if the dev has the tool
  configured.
- CI (`.github/workflows/backend-pr.yml`) uses `actions/setup-java@v4`
  with `java-version: '21'`, so CI is insulated from local `$JAVA_HOME`
  drift. Only local dev is affected.

## Common Issues to Watch For

Project-specific gotchas derived from the chosen stack. Check here first before adding a new log entry — if your error matches one of these, reference the section in your log entry's **Prevention** field.

### Spring Boot 3.3 + Java 21

- **`jakarta.*` vs `javax.*` imports.** Spring Boot 3 is on Jakarta. Old Spring Boot 2 Stack Overflow answers paste `javax.persistence.*`. Use `jakarta.persistence.*` — the compile error is clear but the fix path is misleading without this context.
- **Virtual threads are opt-in.** Enable via `spring.threads.virtual.enabled=true`. Don't assume it unless you set it — notification client retries block a platform thread otherwise.
- **OAuth2 resource server 401 with no WWW-Authenticate body.** Spring Security intentionally returns an opaque 401. Enable `logging.level.org.springframework.security=DEBUG` to see the actual JWT validation failure.
- **`@Transactional` on the state machine not rolling back on `RuntimeException` from a post-commit `TransactionSynchronization` callback.** Post-commit callbacks run *outside* the transaction. If notification-svc fails there, the transition has already committed. This is by design (see [MEMO.md](MEMO.md) decision #2) — DLT handles it. Don't try to "fix" it by pulling the notification call inside the transaction.

### PostgreSQL 16 + Hibernate 6 + Flyway

- **`ddl-auto` must be `none` or `validate` in all envs.** Brief requires Flyway-only schema. If `ddl-auto=update` sneaks in via a test profile, production schema drift will appear weeks later.
- **`Instant` vs `LocalDateTime`.** Columns are `TIMESTAMPTZ` (always UTC). Map to `java.time.Instant` in entities. `LocalDateTime` loses the zone and will produce a bug that only shows on DST weeks.
- **`ON DELETE SET NULL` + Hibernate cascade.** `carriedForwardFromId` and `carriedForwardToId` self-reference `weekly_commit`. Delete order matters: a DELETE on the referent without the DB setting NULLs via the FK constraint would violate FK. Our migration sets `ON DELETE SET NULL`; verify it in `FlywayMigrationIT`.
- **Constraint name collisions.** Flyway `V1`–`V6` must use explicit `CONSTRAINT <name>` for every check and FK; auto-generated names differ across Postgres versions and break idempotent rebuilds.
- **Testcontainers `ryuk` disabled on macOS Sequoia.** If Testcontainers hangs on container reaping, export `TESTCONTAINERS_RYUK_DISABLED=true` locally (never in CI).

### React 18 + Vite 5 + Module Federation

- **Duplicate React in the remote.** If `react` / `react-dom` aren't marked `eager: false` AND not declared as shared in both host and remote, you get two React copies → "Invalid hook call" at runtime. Host-pinned singletons are the pattern.
- **`react-router-dom` version drift.** Host and remote must agree exactly on the version. v6 and v7 differ in `Route` API. Pin both sides.
- **Module Federation + Vite dev HMR.** HMR can serve a stale `remoteEntry.js` after a shared-dep change. Full reload is required. Document this in `apps/weekly-commit-ui/README` if it becomes a recurring dev-experience bug.
- **CloudFront serving stale `remoteEntry.js`.** Versioned paths must be `immutable`; the manifest must be `no-cache`. Flipping these produces stuck clients. Verify on every remote deploy.

### TypeScript strict + RTK Query

- **`strictNullChecks` + `noUncheckedIndexedAccess`.** Array access returns `T | undefined`. This catches the exact kind of silent bug we want caught — don't disable it when noisy.
- **RTK Query tag invalidation.** A mutation that invalidates `Plan` but not `Commit` leaves the commits list stale. Every mutation's `invalidatesTags` must match what changed server-side. Cross-reference the [TECH_STACK.md](TECH_STACK.md#api-endpoints-summary) table when adding a mutation.
- **RTK Query 409 middleware.** Must short-circuit *before* the error reaches the component; otherwise two toasts fire (one global, one local).

### Auth0 / OAuth2

- **`manager_id` claim staleness.** Auth0 propagates JWT refresh within ~15 min. Changes to `manager_id` in the Auth0 user profile don't apply instantly. Tests should seed JWTs with the target claim rather than mutating Auth0 mid-test.
- **Missing `roles` claim.** The custom `roles` claim must be added via Auth0 Action; missing it means every manager endpoint returns 403. First-day setup error.
- **Audience mismatch.** `AUTH0_AUDIENCE` env var must match the API identifier in Auth0 dashboard. Silent 401 if not.

### AWS / CI/CD

- **Shedlock table missing in a new env.** Include in Flyway `V6` or migration for the env; scheduled jobs will run on every pod if Shedlock can't acquire its lock.
- **HPA CPU target vs. virtual threads.** Java virtual threads make CPU utilization deceptively flat. Scale also on memory + 95th-percentile response time if HPA feels unresponsive.
- **CloudFront cache invalidation on remote deploy.** Invalidate the manifest only; leave versioned bundles cached. Invalidating `/remotes/weekly-commit/*` is expensive and unnecessary.
- **ArgoCD sync failing silently when ECR image tag is immutable but digest is identical.** Push a new tag per deploy; don't reuse tags.

### RCDO / notification-svc (external integrations)

- **RCDO 404 mid-flight when an outcome is deleted upstream.** The commit keeps its `supportingOutcomeId`; the hydrated label goes null. UI must handle `supportingOutcome=null` gracefully (show "outcome removed" chip). Backend does NOT refuse the response.
- **notification-svc 429 rate limits.** Resilience4j retry must respect `Retry-After`. A blind retry loop will be rate-limited further.
- **Circuit breaker half-open flapping.** If downstream recovery is slow, half-open can flap. Tune `waitDurationInOpenState` higher (30s+) for notification-svc specifically; RCDO can stay at default.

### Testing

- **Testcontainers Postgres + Flyway slow test startup.** Share the container across tests via `@Testcontainers(disabledWithoutDocker = true)` + Singleton pattern. Starting one container per test class triples CI time.
- **Cypress + Cucumber step collisions.** Two `.feature` files with identically-worded steps produce "Multiple step definitions match" errors. Scope step files per feature or use unique phrasing.
- **Playwright flakes from Module Federation shared-dep load timing.** `page.waitForFunction(() => window.__webpack_share_scopes__)` is more reliable than a bare `networkidle`.
