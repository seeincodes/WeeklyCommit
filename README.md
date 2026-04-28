# Weekly Commit Module

Micro-frontend module that replaces 15-Five for weekly planning, enforcing a
structural link between every weekly commit and a Supporting Outcome in the
org's RCDO hierarchy (Rally Cry → Defining Objective → Core Outcome →
Supporting Outcome). Ships the full commit → lock → reconcile → review
lifecycle with reflection notes, carry-forward flagging, and manager rollups.

Designed to live as a federated remote inside the Performance Assessment
(PA) host app, mirroring the existing Performance Management remote. Also
runnable as a standalone single-origin service for demo / preview deploys.

## Quick start

### Run the demo locally (docker-compose)

```bash
docker compose -f docker-compose.demo.yml up --build
# wait ~3-4 min on first run, then open:
open http://localhost:5173/
```

Three containers come up: Postgres, the Spring Boot backend (e2e + demo
profiles, in-process RCDO stub, demo data seeded), and the Vite-built
frontend served by nginx. The backend signs its own JWTs via the
[`devAuth` shim](apps/weekly-commit-ui/src/dev/devAuth.ts) — no Auth0
tenant needed.

Switch identity with `?devRole=`:

| URL                                    | Identity                       |
|----------------------------------------|--------------------------------|
| `http://localhost:5173/`               | Ada Lovelace (manager)         |
| `?devRole=IC`                          | Ben Carter (IC, reports to Ada)|
| `?devRole=IC_NULL_MANAGER`             | Frankie Hopper (unassigned)    |
| `?devRole=ADMIN`                       | Site Admin                     |

### Run the components individually

| Workspace              | Dev command                                              | Port |
|------------------------|----------------------------------------------------------|------|
| Backend                | `cd apps/weekly-commit-service && ./mvnw spring-boot:run`| 8080 |
| Frontend (standalone)  | `yarn workspace @wc/weekly-commit-ui dev`                | 4184 |
| Backend tests          | `cd apps/weekly-commit-service && ./mvnw verify`         | —    |
| Frontend tests         | `yarn workspace @wc/weekly-commit-ui test`               | —    |

The standalone frontend is a `HashRouter`-based SPA with the dev-auth shim
active; the federated build (consumed by the PA host) lives at
`dist/assets/remoteEntry.js` and skips the standalone wrapper.

## Architecture

| Layer    | Stack                                                                    |
|----------|--------------------------------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.3, Postgres 16.4, Hibernate 6, Flyway, Resilience4j, Shedlock, Auth0 OAuth2 |
| Frontend | React 18, TypeScript 5 strict, Vite 5 + Module Federation, RTK Query, Tailwind + Flowbite |
| Infra    | AWS ECS / RDS / CloudFront *(dormant)*, Railway *(active demo)*          |
| Build    | Yarn workspaces + Nx; Maven for backend; GitHub Actions; ArgoCD *(prod target)* |

Read [docs/TECH_STACK.md](docs/TECH_STACK.md) for the locked tech stack and
[docs/MEMO.md](docs/MEMO.md) for architecture decisions and their rationale.

## Repository layout

```
apps/
  weekly-commit-service/   Spring Boot backend (state machine, RCDO + notification clients,
                           audit log, scheduled jobs, demo seeder)
  weekly-commit-ui/        Vite + React federated remote (IC + Manager surfaces,
                           RCDO picker, reconcile table, manager rollup)
libs/
  contracts/               OpenAPI-generated DTO types shared between backend + frontend
  rtk-api-client/          RTK Query API definitions consumed by the UI
  ui-components/           Placeholder; component sharing across remotes lives here later
infra/
  terraform/
    bootstrap/             TF state bucket + DynamoDB lock + GitHub OIDC role
    demo-deploy/           AWS demo stack (VPC, ECS Fargate, RDS, ALB, CloudFront, S3)
    apply.sh               One-shot AWS apply script
  terraform/monitoring/    CloudWatch alarms (consumed by demo-deploy)
  railway/SETUP.md         Railway dashboard walkthrough (active demo target)
docs/
  PRD.md                   Product requirements + MVP checklist
  MEMO.md                  Architecture decisions + rationale
  TASK_LIST.md             Phased work breakdown
  TECH_STACK.md            Locked tech-stack decisions
  USER_FLOW.md             User journey + API endpoints
  TESTING_STRATEGY.md      Coverage matrix + test conventions
  ERROR_FIX_LOG.md         Incident log; check before adding a new error entry
  runbook.md               State-machine recovery, DLT replay, deploys
.github/workflows/
  backend-pr.yml           Backend CI (Spotless, SpotBugs, mvn verify, JaCoCo gate)
  frontend-pr.yml          Frontend CI (lint, typecheck, vitest, build, Playwright smoke)
  e2e-pr.yml               Cross-remote E2E (Cypress + Cucumber against host harness)
  terraform-pr.yml         Terraform validate + fmt on PRs touching infra/terraform/
  deploy-demo.yml          AWS deploy on push to main (dormant; needs AWS_DEPLOY_ROLE_ARN)
  deploy-railway.yml       Railway deploy on push to main (active; needs RAILWAY_TOKEN)
  mutation-test.yml        Nightly PITest mutation score on the state machine
```

## Deployment

Two paths exist in the repo. **Only one is the active demo target at any
time.** The choice is recorded in [docs/MEMO.md decisions
#12-#13](docs/MEMO.md).

### Railway (active demo target, ~$5-7/mo)

Single-service: backend serves both the SPA bundle and the API.

1. Follow [`infra/railway/SETUP.md`](infra/railway/SETUP.md) once
   (~10 minutes of dashboard clicks: project, Postgres add-on, env vars,
   token).
2. `gh secret set RAILWAY_TOKEN --body "<your token>"`.
3. Push to main; `deploy-railway.yml` builds and prints the URL.

### AWS (dormant; ~$43/mo when running)

Two-origin: S3 + CloudFront for the frontend, ECS Fargate + ALB for the
backend, RDS for Postgres.

1. Follow the apply procedure in
   [`infra/terraform/demo-deploy/README.md`](infra/terraform/demo-deploy/README.md)
   or run [`./infra/terraform/apply.sh`](infra/terraform/apply.sh) once.
2. Push to main; `deploy-demo.yml` deploys.
3. `terraform destroy` (in both stacks, demo-deploy first) when done.

### PRD-aligned production target (deferred)

The PRD calls for EKS + HPA + Multi-AZ RDS + ArgoCD + Module Federation
into the existing PA host with a real Auth0 tenant. Neither demo path
matches this; both are deliberately scoped down for cost and ease.
Tracked in [`docs/TASK_LIST.md` group 14](docs/TASK_LIST.md).

## Contributing

- Read [CLAUDE.md](CLAUDE.md) first — the agent guardrails describe the
  guardrails any human contributor should also respect (tech-stack lock,
  env-protection rules, error-logging policy).
- Open PRs against `main`. Branch name: `task/<group-number>-<short-slug>`.
- Backend: `mvn spotless:apply spotless:check` before commit; CI runs
  spotless + spotbugs + 260 unit tests + 39 ITs + JaCoCo ≥80% gate.
- Frontend: `yarn lint`, `yarn format:check`, `yarn workspace
  @wc/weekly-commit-ui run typecheck`, 172 vitest. CI runs all of these
  plus a `vite build` and Playwright smoke.
- Don't touch `.env` or `.env.*` files without explicit confirmation —
  see [CLAUDE.md § Environment Protection](CLAUDE.md).

## Status

| | |
|---|---|
| MVP requirements | 23/24 complete (24 = host-side kill switch, deferred to host team) |
| Backend test coverage | 80% line, 80% branch (JaCoCo gate enforced) |
| Frontend test coverage | 87.5% line, 84.2% branch (vitest gate enforced) |
| State-machine mutation score | Targeted ≥70% (PITest, nightly) |
| P95 performance targets | Defined in PRD; load-test pending group 18 |
| Live demo URL | Pending Railway dashboard setup ([`infra/railway/SETUP.md`](infra/railway/SETUP.md)) |

## License

Internal use only. Not for public distribution.
