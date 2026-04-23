# Weekly Commit Module — Agent Guardrails

Project docs live in [docs/](docs/). Start with [docs/PRD.md](docs/PRD.md), [docs/TECH_STACK.md](docs/TECH_STACK.md), and [docs/MEMO.md](docs/MEMO.md) to load context before planning any change.

## Environment Protection

- Never modify `.env` or any `.env.*` file without explicit user confirmation. If a change seems required (new service, new credential), propose the diff and stop.
- Never commit `.env` or `.env.*.local` files. They're ignored in [.gitignore](.gitignore); keep them that way.
- Never print, log, or display real API key values, JWTs, or secrets. If asked to show env var contents, show the variable name and confirm presence only.
- Never hardcode secrets. Every credential resolves from an environment variable declared in [.env](.env) with an empty value template.
- When adding a new env var: (1) add to [.env](.env) with a comment explaining purpose, (2) add to [docs/TECH_STACK.md](docs/TECH_STACK.md#environment-variables), (3) wire it into Spring `application.yml` or Vite config, (4) update GitHub Actions secrets if CI needs it.

## Error Logging

Log to [docs/ERROR_FIX_LOG.md](docs/ERROR_FIX_LOG.md) any time you hit one of the following and the fix took more than 5 minutes to arrive at:

- Build failures (Maven, Gradle, Vite, Nx) that required a non-obvious change to resolve
- Runtime errors in dev, staging, or production — especially anything from the state machine, Shedlock, or notification-svc path
- API contract errors (RCDO, notification-svc, Auth0) — both malformed requests and unexpected responses
- Database errors: Flyway migration failures, JPA constraint violations, Hibernate insert-order or cascade surprises, deadlock traces
- Deployment errors: ArgoCD sync failures, EKS pod crash loops, CloudFront cache misbehavior, S3 upload failures
- Module Federation runtime errors: duplicate React, shared-dep version drift, `remoteEntry.js` load failures
- State-machine transition failures, optimistic-lock 409 storms, DLT accumulation incidents

Do NOT log:

- Typos fixed in the same commit
- Linter warnings you addressed immediately
- Expected test failures during TDD (the test itself is the record)
- Validation errors that are the literal point of a unit test

When logging, use the template at the top of [docs/ERROR_FIX_LOG.md](docs/ERROR_FIX_LOG.md). Always include a **Prevention** field — if a new test, lint rule, or alarm would have caught it earlier, name it explicitly. If the error matches a pattern in "Common Issues to Watch For," reference that section.

## Tech Stack Lock

The following technology decisions are locked for v1 per [docs/TECH_STACK.md](docs/TECH_STACK.md) and brief requirements. Do not switch any of these without explicit user approval. New dependencies require justification against existing stack choices.

### Backend

- **Java 21** — do not downgrade; do not switch to Kotlin, Scala, or other JVM languages.
- **Spring Boot 3.3** — do not switch to Quarkus, Micronaut, Helidon, or plain servlets. Do not downgrade to Spring Boot 2.x (Jakarta imports required).
- **PostgreSQL 16.4** — do not switch to MySQL, CockroachDB, DynamoDB, MongoDB, or any other store. Do not downgrade Postgres version without confirming RDS availability.
- **Hibernate 6.x + Spring Data JPA** — do not switch to jOOQ, MyBatis, raw JDBC template (for domain entities). Raw SQL acceptable inside repository `@Query` annotations for performance.
- **Flyway** — do not switch to Liquibase. Do not enable Hibernate `ddl-auto=update` or `create` in any profile; `none` or `validate` only.
- **MapStruct** — do not switch to ModelMapper, manual mappers, or Jackson trickery for DTO conversion.
- **Resilience4j** — do not switch to Hystrix (deprecated), Spring Retry, or Failsafe.
- **Shedlock (JDBC provider)** — do not switch to Quartz, ElasticJob, or database-less coordination.
- **Auth0 (OAuth2 resource server)** — do not switch to Keycloak, Okta, Firebase Auth, or custom auth.
- **Synchronous notification + DLT** — do not introduce the outbox pattern, Kafka, or SQS/SNS. SQS/SNS is a v2 deferral pending ratification (§12 Q16).

### Frontend

- **React 18** — do not upgrade to React 19 without host coordination. Do not switch to Preact, Solid, Svelte, Vue.
- **TypeScript 5.x strict** — do not relax `strict`, `noImplicitAny`, `strictNullChecks`, or `noUncheckedIndexedAccess`. Any `any` requires an adjacent `// eslint-disable-next-line` with justification.
- **Vite 5 + `@originjs/vite-plugin-federation`** — do not switch to Webpack 5, Rspack, or Turbopack. Module Federation is required.
- **Redux Toolkit + RTK Query** — do not switch to Zustand, Jotai, React Query / TanStack Query, Apollo. All server state flows through RTK Query with tag invalidation.
- **Tailwind CSS + Flowbite React** — do not introduce custom CSS files, CSS modules, Emotion, or styled-components. Utility classes only. Headless UI + Tailwind is the approved fallback if Flowbite tokens can't override against the host design system.
- **Vitest** — do not switch to Jest.
- **Playwright** for remote-in-isolation smoke; **Cypress + Cucumber/Gherkin** for cross-remote E2E. Do not switch to WebdriverIO, Puppeteer, TestCafe.

### Infra / build

- **Yarn Workspaces + Nx** monorepo — do not switch to pnpm, Lerna, Turborepo without migration plan.
- **AWS EKS + CloudFront + S3 + RDS Postgres 16.4 Multi-AZ** — do not switch cloud provider. Do not introduce Fargate, Lambda, or EC2-direct for the service.
- **GitHub Actions + ArgoCD** — do not switch to CircleCI, Jenkins, Flux.
- **Sentry + CloudWatch + Micrometer** — do not switch to Datadog, New Relic, OpenTelemetry collector without confirming CloudWatch stays as the metrics destination.

### Architecture locks

- **One Spring Boot deployment.** Do not split into api/scheduler/worker microservices. Scale via HPA on CPU.
- **State machine lives in service code.** Never in DB triggers. Never duplicated into controllers.
- **Optimistic locking on `WeeklyPlan` only.** Do not add `@Version` to `WeeklyCommit`.
- **1:1 commit → Supporting Outcome.** Do not add M:N junction in v1 without user approval (extensible later).
- **Explicit blank state.** Do not auto-create plans on route entry. `POST /plans` is the only creation path.
- **Carry-forward cap 52 hops.** Do not remove the cap.
- **Week math always UTC at service layer.** Never compare against `NOW()` in SQL — always application-computed `Instant`.
- **Top Rock is derived, not stored.** Do not add a `topRockCommitId` column.

### Brief deviations (require stakeholder ratification per §12)

Three brief items are deferred to v2. Do not restore to v1 without confirmation:

- **Outlook Graph integration** (§12 Q15) — v1 ships free-text `relatedMeeting`.
- **SQS/SNS event bus** (§12 Q16) — v1 uses synchronous send + DLT.
- **`RECONCILING` lifecycle state** (§12 Q17) — v1 uses 3 states (DRAFT → LOCKED → RECONCILED); reconciliation is a UI mode on `LOCKED` past day-4.

If the user asks for any of these, confirm whether this is the v2 scope conversation (update [docs/TASK_LIST.md](docs/TASK_LIST.md) Phase 2/3) or a v1 scope change (update [docs/PRD.md](docs/PRD.md) scope boundaries and MEMO decisions #2, #5, #9).
