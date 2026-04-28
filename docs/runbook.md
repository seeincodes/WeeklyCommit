# Runbook — Weekly Commit Module

Operational guide for running, demoing, and recovering the Weekly Commit module.

---

## Local demo (docker-compose)

The demo stack brings up Postgres + the backend + the frontend in three
containers, all networked together. The frontend mints a self-signed JWT for
a seeded MANAGER user, so authentication works without an Auth0 tenant.

### Prerequisites

- Docker + `docker compose` (Docker Desktop on macOS is fine)
- ~2 GB free RAM
- Ports `5173`, `8080`, `5432` available on the host

### Start the demo

```bash
docker compose -f docker-compose.demo.yml up --build
```

First run takes ~3-4 minutes (Maven dep resolve + Vite build + nginx + RDS
init). Subsequent runs reuse the build cache and start in ~30 seconds.

Open <http://localhost:5173/> when the logs show:

```
backend-1 | Started WeeklyCommitServiceApplication in <N>s
backend-1 | [demo-seed] done — manager=22222222-..., currentWeek=YYYY-MM-DD
frontend-1 | nginx: ready
```

### What you'll see

The URL opens to the IC view as **Ada Lovelace** (manager-role). Use the
following query params to switch identity without restarting:

| URL                                                     | Identity                              |
|---------------------------------------------------------|---------------------------------------|
| `http://localhost:5173/`                                | Ada Lovelace (manager)                |
| `http://localhost:5173/?devRole=IC`                     | Ben Carter (IC reporting to Ada)      |
| `http://localhost:5173/?devRole=IC_NULL_MANAGER`        | Frankie Hopper (IC, no manager)       |
| `http://localhost:5173/?devRole=ADMIN`                  | Site Admin (admin endpoints unlocked) |

The seeded data:
- **Current week:** all four named ICs have a DRAFT plan with 2-3 commits each.
  Ada has a Top Rock; Ben/Cleo have a Top Rock; Dax does not (NO_TOP_ROCK
  flag surfaces on the manager rollup).
- **Prior week:** Ben/Cleo/Dax have RECONCILED plans with realistic
  completion outcomes. Cleo's plan is unreviewed (UNREVIEWED_72H flag);
  Dax has a MISSED commit (visible in the carry-forward affordances).

### Stop / reset

```bash
# Stop everything but keep the seeded DB:
docker compose -f docker-compose.demo.yml down

# Reset back to the seeded state (drops Postgres volume):
docker compose -f docker-compose.demo.yml down --volumes
```

The seeder is idempotent — it detects the manager UUID on subsequent boots
and skips re-seeding, so leaving the volume in place preserves any commits
you create during the demo.

### Common issues

| Symptom                                            | Cause                                        | Fix                                                                                       |
|----------------------------------------------------|----------------------------------------------|-------------------------------------------------------------------------------------------|
| Backend container exits with `connect refused`     | Postgres not ready before backend started    | Compose's `depends_on: condition: service_healthy` should prevent this; if it persists, increase the start_period in the backend healthcheck |
| Frontend 404s on `/api/v1/...`                     | `VITE_API_BASE_URL` build-arg wrong          | Rebuild with `--build-arg VITE_API_BASE_URL=http://localhost:8080`                        |
| Page loads but every fetch is 401                  | devAuth shim didn't bundle (build flag wrong)| Rebuild frontend with `--build-arg VITE_DEMO_MODE=true`; check browser console for `[dev-auth] init failed`|
| RCDO picker shows "couldn't load outcomes"         | StubRcdoController didn't load               | Confirm `SPRING_PROFILES_ACTIVE` includes `demo` in the backend logs                       |
| Lock Week / Submit Reconciliation returns 500      | State machine transitions writing audit rows; check audit_log permissions  | Check backend logs; the demo profile inherits the same audit infra as prod                 |

---

## AWS demo deploy

See [`infra/terraform/demo-deploy/README.md`](../infra/terraform/demo-deploy/README.md)
for the full apply procedure. Quick version:

```bash
# 1. Bootstrap (one time, your AWS-credentialed shell)
cd infra/terraform/bootstrap
terraform init
terraform apply

# 2. Save the four bootstrap outputs as repo secrets/vars
gh secret set AWS_DEPLOY_ROLE_ARN --body "$(terraform output -raw github_actions_role_arn)"
gh variable set TF_STATE_BUCKET --body "$(terraform output -raw tf_state_bucket)"
gh variable set TF_LOCK_TABLE --body "$(terraform output -raw tf_lock_table)"
gh variable set AWS_REGION --body "us-east-1"

# 3. First apply of the demo stack (placeholder image; first push to main fixes it)
cd ../demo-deploy
terraform init \
  -backend-config="bucket=$(cd ../bootstrap && terraform output -raw tf_state_bucket)" \
  -backend-config="dynamodb_table=$(cd ../bootstrap && terraform output -raw tf_lock_table)" \
  -backend-config="region=us-east-1"
terraform apply -var "image_tag=placeholder"

# 4. The next push to main triggers .github/workflows/deploy-demo.yml,
#    which builds the real image, deploys it, and prints the live URL.

# 5. Get the live URL anytime:
terraform output -raw cloudfront_url
```

Cost: ~$43/month while running. `terraform destroy` (in both stacks, demo-deploy first)
to bring the bill back to zero.

### Recovery from a stuck deploy

If `deploy-demo.yml` hangs at `aws ecs wait services-stable`:

1. Check the ECS service events: `aws ecs describe-services --cluster weekly-commit-demo-cluster --services weekly-commit-demo-backend`
2. Common causes:
   - **Image pull failure** — first deploy with no image yet. Push manually with `docker push <ecr-uri>:<sha>` and re-run the workflow with `skip_terraform: true`.
   - **Health check failing** — the task is starting but `/actuator/health/readiness` is returning non-200. Check ECS task logs in CloudWatch (`/ecs/weekly-commit-demo/backend`).
   - **Subnet routing** — the public subnet's route table is missing the IGW route. Re-apply terraform.
3. To force-stop a stuck deploy: `aws ecs update-service --cluster ... --service ... --desired-count 0` then re-deploy with `--desired-count 1`.

### Recovery from CloudFront serving stale content

CloudFront caches the bundle. After a successful deploy, if users see the old version:

1. The deploy workflow already invalidates `/index.html`. If you suspect a cache-policy bug, manually invalidate everything:
   ```bash
   aws cloudfront create-invalidation \
     --distribution-id $(cd infra/terraform/demo-deploy && terraform output -raw cloudfront_distribution_id) \
     --paths "/*"
   ```
2. If the bundle's hashed filenames are correct but the served `index.html` references old hashes, the S3 sync's `--delete` flag should have already removed the old bundle. Confirm with `aws s3 ls s3://<bucket>`.

---

## State-machine recovery

If a `WeeklyPlan` ends up in a state the state machine considers invalid (e.g.
`reconciled_at` set but `state = LOCKED`), the recovery path is:

1. Identify the plan id from the audit log (`GET /audit/plans/{id}` or query
   `audit_log` directly with the manager's role token).
2. Manually correct the state via direct SQL — the state machine deliberately
   has no "force-set" admin endpoint to prevent shortcut abuse.
3. Re-emit the audit row with `actor='ops:<your-name>'` and a reason in
   `payload->>'note'` so the recovery is traceable.

```sql
UPDATE weekly_plan
SET state = 'RECONCILED',
    reconciled_at = NOW(),
    last_modified_by = 'ops:ada'
WHERE id = '<plan-id>';

INSERT INTO audit_log (id, plan_id, actor, action, payload, created_at)
VALUES (gen_random_uuid(), '<plan-id>', 'ops:ada', 'STATE_RECOVERY',
        '{"note":"Recovered from inconsistent state per runbook"}',
        NOW());
```

---

## DLT replay

If `notification_dlt` accumulates rows (CloudWatch alarm fires), the recovery
path is:

1. List failed notifications: `SELECT id, attempt_count, last_error, created_at
   FROM notification_dlt ORDER BY created_at DESC;`
2. Inspect the underlying error to confirm it's transient (rate-limit, DNS,
   timeout) vs. permanent (validation error).
3. For transient: replay via `POST /api/v1/admin/notifications/dlt/{id}/replay`
   with an ADMIN-role JWT. The endpoint is synchronous send-and-delete in one
   transaction; on success the row is removed. Repeat per id; there's no batch
   endpoint by design (each replay logs an audit entry).
4. For permanent: do not replay. Investigate the upstream `notification-svc`,
   confirm the schema mismatch / config issue is resolved, then either:
   - **Replay if recoverable** after the upstream fix
   - **Tombstone** by marking the DLT row deleted with a reason in `payload`

The CloudWatch alarm `weekly-commit-notification-dlt-recent` fires when any
DLT row is < 1 hour old. It does NOT fire for older rows — once you've
acknowledged, you have until 1 hour from the next failure.

---

## Scheduled-job re-run

If `auto-lock`, `archival`, or `unreviewed-digest` fails (visible in the
`weekly-commit-scheduled-job-failures` CloudWatch alarm or via the `JobMetrics`
counter), the recovery path is:

1. Confirm the Shedlock row is released: `SELECT * FROM shedlock WHERE name =
   '<job-name>';` — `lock_until < NOW()` means the lock is free.
2. Trigger the job manually by toggling the cron config. Either:
   - SSH into a pod and run the relevant Spring `@Scheduled` method via JMX (cumbersome)
   - Adjust the cron expression to fire imminently (`weekly-commit.scheduled.<job>-cron`),
     deploy, and watch the next run
3. If the job consistently fails on the same input, the underlying bug is in
   the application code — fix and redeploy, don't keep retrying.

---

## Remote rollback

The frontend remote is served from S3 + CloudFront with content-hashed
filenames. To roll back to a previous version:

1. Identify the previous bundle's prefix in S3 (`/remotes/weekly-commit/{sha}/`).
2. Update the host's manifest pointer to the previous SHA.
3. CloudFront caches the manifest with `Cache-Control: no-cache`, so the
   rollback propagates within seconds. Hashed asset bundles continue to work
   from cache.

For the demo deploy this doesn't apply — the demo doesn't use Module Federation
runtime resolution; it ships a single SPA bundle.

---

## Legal escalation

If the audit retention requirement (2 years per [MVP17](PRD.md)) is breached
because of inadvertent data deletion or DB restore-to-earlier-snapshot, the
escalation path is:

1. Stop accepting new audit writes until the retention guarantee is re-established.
   Block the `audit_log` insert path by toggling `weekly-commit.audit.enabled=false`
   on the backend (config flag exists; defaults to true).
2. Notify legal counsel within 24 hours per the data-handling policy.
3. Restore from the most recent backup that preserves 2y retention.
4. Do NOT manually re-create lost audit rows — the integrity of the audit log
   depends on the rows being authentic, not reconstructed.

This path has not been exercised; treat it as a procedure-of-record that
needs a real drill before any production dependency is taken on it.
