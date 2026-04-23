# ADR-0001 — RCDO read API contract

- **Status:** Proposed (stubbed)
- **Date:** 2026-04-23
- **Deciders:** Xian (solo driver); RCDO service owner TBD
- **Relates to:** Presearch §8 RCDO, PRD [MVP3], TASK_LIST group 2, assumption A1

## Context

Commits link 1:1 to a Supporting Outcome in the RCDO hierarchy. We consume
RCDO read-only — no writes, no caching on the backend in v1. The picker
needs a list endpoint with enough metadata to render a 4-level breadcrumb
without N+1 follow-ups; commit reads need a single-outcome hydration endpoint
that survives upstream deletions gracefully.

Presearch assumption A1 names this as a blast-radius-high risk: if the API
differs from what we assumed, the data layer rewrites.

**Inputs for this ADR are invented.** The spike was supposed to hit the real
service; it did not. Stub payloads live in [../spikes/rcdo-sample-responses.json](../spikes/rcdo-sample-responses.json).
This ADR is downgraded to **Proposed (stubbed)** until a real capture
replaces them.

## Decision

Contract (stubbed):

**Endpoints**
- `GET /rcdo/supporting-outcomes?orgId={orgId}&active=true` — picker list
- `GET /rcdo/supporting-outcomes/{id}?hydrate=full` — single hydration

**Response shape**
- `{ data, meta }` envelope (matches our own convention, coincidentally).
- Supporting Outcome carries a `breadcrumb` object with all four levels
  inline — `rallyCry`, `definingObjective`, `coreOutcome`, `supportingOutcome`,
  each `{ id, label }`. This avoids a second fetch to render the breadcrumb.
- `meta.etag` is a weak ETag we can use for conditional requests later (not
  in v1 scope).

**Error shape**
- Non-2xx returns `{ error: { code, message, ...extras } }`.
- 404 on deleted/deactivated outcome is the *expected* behavior when a
  commit references an upstream-deleted outcome. Our backend maps this to
  `supportingOutcome=null` on the commit DTO and a `deletedUpstream=true`
  flag — the commit row is preserved.

**Auth**
- Service-to-service bearer token. Header `Authorization: Bearer <token>`.
  Token source TBD with RCDO owner (service account in Auth0 or a separate
  mTLS identity).

**SLOs (assumed)**
- P95 < 200 ms for both endpoints at our call volume (~500 picker opens/day,
  ~3000 hydrations/day). Client timeout set to 2s (`RCDO_TIMEOUT_MS`).

## Alternatives considered

1. **Server-side cache of RCDO payloads in weekly-commit-service.** Deferred.
   Adds invalidation questions (when does RCDO rename propagate?) that aren't
   worth solving until RTK Query's 10-min client cache proves insufficient
   in tracing. See MEMO decision #6.
2. **Replicate the RCDO tree into our own tables.** Rejected. We'd be owning
   data we don't want to own and fighting every upstream rename.
3. **Fetch breadcrumb separately via a parent-walk endpoint.** Rejected.
   Adds N+1 latency on the picker and makes the picker typeahead feel heavy.
   Inline breadcrumb at the leaf is the correct shape for a read-only consumer.

## Consequences

- **Positive**: backend work in groups 6 + 7 can proceed against a well-typed
  stub. `RcdoClient` + `RcdoClientContractTest` (WireMock) can be written
  against the fixture file — the WireMock scenarios are the contract
  checkpoint.
- **Negative**: if the real RCDO contract differs in any material way —
  envelope, breadcrumb shape, error code vocabulary — code written against
  this stub breaks at the client seam. Blast radius is contained to
  `RcdoClient` + its mapper and the DTO layer if we keep breadcrumb as an
  opaque blob in our own types until we validate.
- **Follow-ups**:
  - Real capture before group 7 ships production (write to
    `docs/spikes/rcdo-sample-responses.json`; this ADR flips to **Accepted**
    or a new ADR supersedes).
  - WireMock fixtures in `apps/weekly-commit-service/src/test/resources/wiremock/rcdo/`
    mirror the stub exactly.
  - `RcdoClient` exposes a domain interface; the HTTP transport stays
    replaceable without touching callers.

## Validation

Upgrades to **Accepted** when all four are true:

1. A real curl capture from RCDO lives at `docs/spikes/rcdo-sample-responses.json`,
   replacing the invented payloads. Git history preserves the stub for
   auditing.
2. `RcdoClientContractTest` passes against the WireMock stubs built from the
   real capture.
3. A one-shot integration test in a non-prod env hits the real RCDO and
   asserts: `data[0]` has `id`, `label`, `breadcrumb.{rallyCry,definingObjective,coreOutcome,supportingOutcome}`
   each present with `id` and `label`.
4. The RCDO service owner confirms the 404-on-deleted contract in writing
   (Slack thread link recorded here).
