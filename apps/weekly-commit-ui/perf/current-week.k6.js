/**
 * current-week.k6.js — k6 regression gate for `GET /api/v1/plans/me/current`.
 *
 * What it does:
 *   Hits the IC's "current week plan" endpoint with a Bearer token (env
 *   WC_PERF_JWT, minted by ./sign-perf-jwt.mjs) and asserts p95 < 200 ms per
 *   docs/PRD.md §Performance Targets. Tag `endpoint:current` scopes the
 *   threshold so it only measures this URL, not k6's own overhead pings.
 *
 * VU profile: 10 VUs × 30s. This is a regression gate, not a load test —
 *   measures steady-state latency on a backend that's already warm.
 *
 * How to run locally:
 *   brew install k6
 *   yarn workspace @wc/weekly-commit-ui run perf:sign-jwt \
 *     | xargs -I{} k6 run -e WC_PERF_JWT={} apps/weekly-commit-ui/perf/current-week.k6.js
 *
 * What fails it:
 *   - p95 >= 200 ms on the tagged request → threshold violation → exit 1 → CI red.
 *   - Any non-200 response → check failure (counted in summary; doesn't fail the
 *     run on its own, but the threshold will trip if errors balloon latency).
 *
 * Scope: runs on k6's Goja runtime, not Node. No npm deps; no TS.
 */

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.WC_PERF_BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.WC_PERF_JWT;

if (!TOKEN) {
  throw new Error('WC_PERF_JWT env var is required (mint via perf:sign-jwt).');
}

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: { 'http_req_duration{endpoint:current}': ['p(95)<200'] },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/plans/me/current`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
    tags: { endpoint: 'current' },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'duration < 1000ms': (r) => r.timings.duration < 1000,
  });
}
