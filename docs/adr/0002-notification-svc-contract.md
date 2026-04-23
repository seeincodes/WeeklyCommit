# ADR-0002 — notification-svc send contract

- **Status:** Proposed (stubbed)
- **Date:** 2026-04-23
- **Deciders:** Xian (solo driver); notification-svc owner TBD
- **Relates to:** Presearch §8 notification-svc, PRD [MVP14] [MVP15], MEMO decision #2, assumption A6

## Context

State-machine transitions send notifications synchronously after transaction
commit (MEMO decision #2). We need a contract for single-send
(`WEEKLY_PLAN_LOCKED`, `WEEKLY_PLAN_RECONCILED_TO_MANAGER`) and a batched
digest (`WEEKLY_UNREVIEWED_DIGEST`, fired by the Monday 09:00 UTC job).

Presearch assumption A6 names this spike as blocking: if notification-svc
doesn't exist or is incompatible, ~1 week of scope adds to v1.

**Inputs for this ADR are invented.** Stub payloads at
[../spikes/notification-svc-sample-responses.json](../spikes/notification-svc-sample-responses.json).
Status is **Proposed (stubbed)** until validated.

## Decision

Contract (stubbed):

**Endpoints**
- `POST /notifications/send` — single send
- `POST /notifications/send/batch` — multi-recipient send (digest)

**Request shape (single)**
- `eventType` — enum; our three v1 events: `WEEKLY_PLAN_LOCKED`,
  `WEEKLY_PLAN_RECONCILED_TO_MANAGER`, `WEEKLY_UNREVIEWED_DIGEST`.
- `recipientEmployeeId` — resolves to email/Slack/etc. inside notification-svc.
- `channel` — `EMAIL` for v1. Future: `SLACK`, `IN_APP`.
- `templateId` — from a v1 catalog we register with notification-svc
  (`weekly-commit.plan-locked.v1`, etc.).
- `templateVars` — template-specific object.
- `metadata.sourceService`, `metadata.planId` — breadcrumbs for debugging.
- Header `X-Idempotency-Key` — mandatory. We set it to a deterministic key:
  `wc-plan-{planId}-{targetState}-v{version}` for state-transition
  notifications; `wc-digest-{managerId}-{isoWeek}` for digests. This
  guarantees retry safety and makes replay idempotent.

**Response shape**
- 202 Accepted with `{ data: { notificationId, status: "QUEUED", acceptedAt } }`.
  notification-svc is async; delivery status is tracked in that service, not
  ours.

**Error handling**
- 400: non-recoverable — do not retry; log and move on. Don't write DLT for
  validation failures since a retry won't help.
- 401: service-token issue; retry with refreshed token once, then DLT.
- 409: duplicate idempotency key — *treated as success*. The response still
  carries `priorNotificationId`; we record that in our audit and consider
  the notification delivered.
- 429: respect `retryAfterSeconds`. Resilience4j retry with dynamic wait.
- 503: retry with exponential backoff, then DLT.

**Timeouts**
- Client `connect` 1s, `read` 3s (`NOTIFICATION_SVC_TIMEOUT_MS=3000`).
- Resilience4j: max 3 attempts total, exponential backoff 500 ms base, 2×
  factor; circuit breaker opens after 50% failure rate in a 30s window,
  stays open 30s before half-open.

**DLT path**
- On all retries exhausted OR circuit open, write a row to
  `notification_dlt` with `eventType`, full `payload`, `lastError`,
  `attempts`. CloudWatch alarm on any row < 1h old.
- Admin replay endpoint (`POST /admin/notifications/dlt/{id}/replay`) reads
  the row, re-issues the POST with the original `X-Idempotency-Key`, and
  deletes the DLT row on success.

## Alternatives considered

1. **Async outbox pattern.** Deferred to v2. At ~75 notifications/day, the
   outbox infra (worker deployment, polling loop, FAILED state reconcile)
   exceeds the benefit. MEMO decision #2 covers this.
2. **Fire-and-forget without DLT.** Rejected. Lost notifications on a
   manager acknowledgement or a lock event are user-visible and
   undebuggable without a durable record.
3. **Per-event endpoint**, e.g. `POST /notifications/weekly-plan-locked`.
   Rejected. Couples notification-svc to our event vocabulary; the
   `eventType` + `templateId` pattern is closer to how notification-svc
   would already be structured if it's a shared service.

## Consequences

- **Positive**: `NotificationClient` and DLT pipeline can be built in group 7
  against WireMock stubs built from this contract. State-machine post-commit
  hook has a firm interface to call.
- **Negative**: if real notification-svc lacks `X-Idempotency-Key` support,
  we lose safe retry and must redesign the DLT replay. This is a bigger
  deal than the RCDO stubs — notification-svc retry *correctness* depends
  on it. Flag for validation priority.
- **Follow-ups**:
  - Real capture before group 7 ships (update stub file; flip status).
  - Register template IDs (`weekly-commit.plan-locked.v1`,
    `weekly-commit.plan-reconciled-to-manager.v1`,
    `weekly-commit.unreviewed-digest.v1`) with notification-svc owner and
    provide subject + variable contracts. Add to this ADR's template catalog
    once confirmed.
  - WireMock fixtures in `apps/weekly-commit-service/src/test/resources/wiremock/notification/`.

## Validation

Upgrades to **Accepted** when all five are true:

1. Real curl capture replaces the invented payloads in the stub file.
2. notification-svc owner confirms `X-Idempotency-Key` semantics match:
   duplicate key returns 409 with `priorNotificationId`, not 202 re-delivery.
3. Three template IDs registered in notification-svc and returning 200 on a
   happy-path send.
4. `NotificationClientContractTest` passes against WireMock.
5. DLT chaos test (inject 10-min outage) shows: all transitions succeed,
   DLT accumulates, alarm fires, admin replay recovers all rows.
