#!/usr/bin/env node
/**
 * sign-perf-jwt.mjs — mints an RS256 Bearer token for the k6 perf harness.
 *
 * What it does:
 *   - Reads the committed test private key at
 *     `apps/weekly-commit-ui/cypress/support/auth/keys/private-key.pem` (the same key
 *     the Cypress + Cucumber suite uses).
 *   - Signs a JWT whose `sub` is the deterministic IC seeded by PerfDataSeeder
 *     (`PERF_IC_ID = 22222222-2222-2222-2222-222222222222`) and whose `org_id` matches
 *     `PERF_ORG_ID` (`33333333-3333-3333-3333-333333333333`). Without that exact pairing,
 *     the backend's `GET /plans/me/current` controller returns 404 instead of the
 *     seeded plan and the perf measurement is meaningless.
 *   - Prints the token to stdout. No newline-handling assumptions; consumer pipes it.
 *
 * Why it mirrors `cypress/support/auth/signTestJwt.ts`:
 *   The backend's e2e-profile JwtDecoder (E2eJwtDecoderConfig) trusts the matching
 *   public key but does not enforce issuer/audience claims (see comments in
 *   docker-compose.e2e.yml line 79-82). The same claim shape that works for the
 *   Cypress suite works here. Required claims per AuthenticatedPrincipal.of:
 *     - sub        (UUID, required)
 *     - org_id     (UUID, required)
 *     - manager_id (UUID, optional — omitted when null)
 *     - roles      (string[]; mapped to ROLE_* authorities by SecurityConfig)
 *     - timezone   (IANA zone string; falls back to UTC if missing/invalid)
 *
 * How to run locally:
 *   brew install k6
 *   yarn workspace @wc/weekly-commit-ui run perf:sign-jwt \
 *     | xargs -I{} k6 run -e WC_PERF_JWT={} apps/weekly-commit-ui/perf/current-week.k6.js
 *
 * What fails it:
 *   - Missing/unreadable PEM file → exit 1 with stderr message.
 *   - Any `jose` signing error propagates as an unhandled rejection (exit 1).
 *
 * Scope: this file is outside `src/` so it's untouched by Vitest/coverage/tsc.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SignJWT, importPKCS8 } from 'jose';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Path is relative to this file's location so the script works regardless of cwd.
const PRIVATE_KEY_PATH = resolve(
  __dirname,
  '..',
  'cypress',
  'support',
  'auth',
  'keys',
  'private-key.pem',
);

// Mirrors PerfDataSeeder constants. Drift here = 404s in the k6 run.
const PERF_IC_ID = '22222222-2222-2222-2222-222222222222';
const PERF_MANAGER_ID = '11111111-1111-1111-1111-111111111111';
const PERF_ORG_ID = '33333333-3333-3333-3333-333333333333';

// Mirrors signTestJwt.ts; the e2e JwtDecoder doesn't validate this claim but
// keeping it identical means token shape stays a single source of truth.
const ISSUER = 'wc-e2e-test-signer';
const TOKEN_TTL_SECONDS = 60 * 60; // 1h — comfortably longer than a 30s k6 run

async function main() {
  const pem = readFileSync(PRIVATE_KEY_PATH, 'utf8');
  const key = await importPKCS8(pem, 'RS256');
  const now = Math.floor(Date.now() / 1000);

  const token = await new SignJWT({
    sub: PERF_IC_ID,
    org_id: PERF_ORG_ID,
    manager_id: PERF_MANAGER_ID,
    roles: ['IC'],
    timezone: 'America/New_York',
  })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer(ISSUER)
    .setIssuedAt(now)
    .setExpirationTime(now + TOKEN_TTL_SECONDS)
    .sign(key);

  // stdout only — caller pipes via xargs. No trailing newline so xargs -I{} sees
  // exactly the token; `process.stdout.write` is explicit about that contract.
  process.stdout.write(token);
}

main().catch((err) => {
  process.stderr.write(`sign-perf-jwt: ${err?.message ?? err}\n`);
  process.exit(1);
});
