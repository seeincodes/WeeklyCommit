# Railway deploy — one-time setup

This walks through the dashboard clicks Railway requires before the
`deploy-railway.yml` workflow can do anything useful. ~10 minutes total.

## Why these steps aren't in code

`railway.toml` covers the *service config* (Dockerfile path, healthcheck,
restart policy). It does not cover:

- Creating the Railway project itself
- Attaching a Postgres add-on
- Linking the GitHub repo so the project knows where source comes from
- Generating a project-scoped API token

These are dashboard actions in Railway's web UI. After they're done once,
the workflow takes over for every push to main.

## Account + project

1. Sign in at <https://railway.app>. Free tier is fine for this demo.
2. Click **New Project** → **Empty Project** (we're going to import the
   GitHub repo manually rather than using the "Deploy from GitHub" wizard
   so the build picks up `Dockerfile.bundled` rather than Nixpacks
   auto-detection).
3. Name it `weekly-commit-demo` (or anything memorable).

## Service: from this repo

1. In the project dashboard click **+ New** → **GitHub Repo**.
2. Authorize Railway to access `seeincodes/WeeklyCommit` (only the one
   repo; do not grant org-wide access).
3. After import, click into the service settings:
   - **Service Name**: `weekly-commit` (must match `vars.RAILWAY_SERVICE`
     in the GHA workflow, which defaults to this value)
   - **Root Directory**: leave blank (the Dockerfile takes the repo root
     as build context)
   - **Build**: should auto-detect `railway.toml` and pick the
     Dockerfile builder. Confirm the Dockerfile path reads
     `apps/weekly-commit-service/Dockerfile.bundled`.

## Postgres add-on

1. **+ New** → **Database** → **Add PostgreSQL**.
2. Railway provisions a managed Postgres and attaches its connection vars
   to the project. The connection vars Railway creates are
   `PGHOST` / `PGPORT` / `PGDATABASE` / `PGUSER` / `PGPASSWORD` and a
   composite `DATABASE_URL`.
3. Spring expects `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME`
   / `SPRING_DATASOURCE_PASSWORD`. Map them in the **weekly-commit**
   service's variables tab:

   ```
   SPRING_DATASOURCE_URL      = jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   SPRING_DATASOURCE_USERNAME = ${{Postgres.PGUSER}}
   SPRING_DATASOURCE_PASSWORD = ${{Postgres.PGPASSWORD}}
   ```

   Railway expands `${{Postgres.X}}` references automatically -- they're
   pointers, not literal env values. The `Postgres.` prefix matches the
   default service name Railway assigns the add-on; if you renamed it,
   adjust the references accordingly.

4. While in the variables tab, set the rest the backend needs:

   ```
   SPRING_PROFILES_ACTIVE  = e2e,demo
   AUTH0_ISSUER_URI        = http://demo-no-issuer/
   AUTH0_AUDIENCE          = demo-no-audience
   RCDO_BASE_URL           = http://localhost:8080
   SCHEDULED_JOBS_ENABLED  = false
   DEMO_SEED               = true
   ```

   `e2e,demo` activates the in-process JWT decoder + RCDO stub +
   `DemoDataSeeder`. The `AUTH0_*` placeholders satisfy Spring's
   property resolver at boot but are never used because the e2e profile
   replaces the resource server's JwtDecoder bean.

## Domain

Railway gives every HTTP service a free `.up.railway.app` subdomain.

1. Service → **Settings** → **Networking** → **Generate Domain**.
2. Copy the resulting URL. Smoke-check it once the first deploy lands.

## Project token for CI

1. Top-right avatar → **Account Settings** → **Tokens** → **Create New
   Token**.
2. Name: `gha-deploy`. Scope: project-scoped to `weekly-commit-demo`
   (NOT account-wide -- a leaked project token is much smaller blast
   radius than an account token).
3. Copy the token. **It's only shown once.** If you lose it, regenerate.

## Wire to GitHub

```bash
gh secret set RAILWAY_TOKEN --body "<paste-token-here>"
gh variable set RAILWAY_SERVICE --body "weekly-commit"  # match the service name above
```

(Or set them via the GitHub repo settings UI under
Settings → Secrets and variables → Actions.)

## First deploy

Push any commit to main (or trigger manually):

```bash
gh workflow run deploy-railway.yml --ref main
gh run watch
```

The workflow:
1. Pre-flights `RAILWAY_TOKEN`. Fails fast with a clear summary if absent.
2. Installs the Railway CLI.
3. Runs `railway up --service weekly-commit --ci`. Railway pulls the repo,
   builds the Dockerfile, deploys.
4. Reports the live URL via `railway domain`.

First build takes ~5-7 minutes (frontend yarn install + vite build +
backend mvn package). Subsequent builds are faster thanks to Railway's
layer cache.

## Cost

Hobby plan: $5/mo flat + ~$0.000463 per vCPU-hour, $0.000231 per GB-RAM-hour
(beyond the $5 credit). At idle, one always-on service on hobby with the
Postgres add-on runs ~$5-7/month.

If usage exceeds the included credit, you'll see it in the project's usage
dashboard before billing. Set a usage cap under
**Account Settings** → **Usage** → **Spending Cap** to enforce a hard ceiling.

## Tear down

Project → **Settings** → **Danger** → **Delete Project**.
Or revoke the deploy token + delete the service from the dashboard.
