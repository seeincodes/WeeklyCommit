# User Flow — Weekly Commit Module

## Primary Flow

Four flows make up the weekly loop. IC-driven flows are 1-3; manager review is flow 4. Steps show the round-trip between UI, backend, and external services, with rough timing annotations.

### Flow 1 — Commit Entry (Monday AM, IC-driven)

```
┌──────────────────────────┐
│ IC opens                 │
│ /weekly-commit/current   │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐      ┌────────────────────────┐
│ UI:                      │◀─────│ GET /plans/me/current  │
│ GET /plans/me/current    │      │ response: 404 (first   │
│                          │      │   visit this week)     │
└──────────┬───────────────┘      └────────────────────────┘
           │  404
           ▼
┌──────────────────────────┐
│ UI renders blank state   │       (~200ms round-trip)
│ "Start your week"        │
│ [Create plan]            │
└──────────┬───────────────┘
           │  IC clicks Create
           ▼
┌──────────────────────────┐      ┌────────────────────────┐
│ UI:                      │─────▶│ POST /plans            │
│ POST /plans              │◀─────│ 201 { weeklyPlan }     │
│                          │      │ (idempotent on         │
│                          │      │  employeeId+weekStart) │
└──────────┬───────────────┘      └────────────────────────┘
           │                           (~300ms)
           ▼
┌──────────────────────────┐
│ UI: DRAFT editor renders │
│   - add commit           │
│   - pick Supporting      │
│     Outcome via          │
│     <RCDOPicker />       │◀── GET /rcdo/supporting-outcomes?orgId=&active=true
│   - pick Rock/Pebble/Sand│       (RTK Query, 10min cache)
│   - drag-reorder Rocks   │
│     (Top Rock = lowest   │
│      displayOrder Rock)  │
│   - optional estimated   │
│     hours, tags          │
└──────────┬───────────────┘
           │  saves each commit
           ▼
┌──────────────────────────┐      ┌────────────────────────┐
│ UI:                      │─────▶│ POST /plans/{id}/      │
│ POST /plans/{id}/commits │      │   commits              │
│                          │◀─────│ 201 { commit }         │
└──────────────────────────┘      └────────────────────────┘
                                       (~150ms per commit)

If ANY carry-forward twins exist from last week, they pre-populate with
<CarryStreakBadge /> ≥ 2. streak ≥ 3 shows the stuck flag.
```

### Flow 2 — Lock Week (Monday EOD or auto-lock)

```
IC path:                                 Auto-lock path (@Scheduled, hourly):
┌──────────────────────┐                 ┌──────────────────────────────┐
│ IC clicks "Lock Week"│                 │ Shedlock-elected pod runs    │
└──────────┬───────────┘                 │ scans state=DRAFT AND        │
           │                             │   weekStart + cutoff <= now  │
           ▼                             └──────────┬───────────────────┘
┌──────────────────────┐                            │ for each plan
│ POST /plans/{id}/    │                            ▼
│   transitions        │                 ┌──────────────────────────────┐
│ body { to: "LOCKED" }│                 │ WeeklyPlanStateMachine       │
└──────────┬───────────┘                 │   .transition(planId,LOCKED) │
           │                             └──────────┬───────────────────┘
           ▼                                        │ same code path
┌─────────────────────────────────────────┐◀────────┘
│ @Transactional {                        │
│   load plan (@Version check)            │
│   validate guards (DRAFT only)          │
│   state = LOCKED                        │
│   lockedAt = now                        │
│   append audit_log STATE_TRANSITION row │
│ } commit                                │
└──────────┬──────────────────────────────┘
           │  post-commit
           ▼
┌─────────────────────────────────────────┐
│ notification-svc                        │
│   synchronous REST POST (Resilience4j:  │
│     3 retries, exp backoff, circuit     │
│     breaker)                            │
│   on exhaustion → write NotificationDLT │
│     → CloudWatch alarm (< 1h rule)      │
└──────────┬──────────────────────────────┘
           │
           ▼
┌──────────────────────────────┐
│ 200 { weeklyPlan } to UI     │   P95 < 300ms (includes notif attempt)
│ commits now immutable except │
│ for actualStatus / actualNote│
│ / reflectionNote             │
└──────────────────────────────┘
```

### Flow 3 — Reconcile (Friday, `weekStart + 4 days`)

```
┌──────────────────────────────┐
│ IC opens /weekly-commit/     │
│   current                    │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│ UI: plan is LOCKED AND       │
│   now >= weekStart + 4 days  │
│   → <ReconcileTable /> opens │
│     in reconciliation mode   │
│                              │
│ Per commit:                  │
│   DONE / PARTIAL / MISSED    │
│   actualNote                 │
│                              │
│ <ReflectionField />          │
│   ≤ 500 chars, plain text    │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐      ┌────────────────────────────┐
│ UI: PATCH per commit         │─────▶│ PATCH /commits/{id}        │
│   (state-aware: only         │◀─────│ 200 { commit }             │
│   actualStatus / actualNote) │      │                            │
│ UI: PATCH plan for note      │─────▶│ PATCH /plans/{id}          │
│                              │◀─────│ 200 { weeklyPlan }         │
└──────────┬───────────────────┘      └────────────────────────────┘
           │  per-field saves ~150ms
           │
           │  IC reviews missed/partial commits
           │  per-item [Carry to next week] +
           │  [Carry all missed/partial]
           ▼
┌──────────────────────────────┐      ┌────────────────────────────┐
│ POST /commits/{id}/          │─────▶│ Creates twin in next       │
│   carry-forward              │      │   week's DRAFT plan        │
│                              │◀─────│   carriedForwardFromId set │
│                              │      │   carriedForwardToId on    │
│                              │      │     source                 │
└──────────┬───────────────────┘      └────────────────────────────┘
           │
           │  IC clicks "Submit reconciliation"
           ▼
┌──────────────────────────────┐      ┌────────────────────────────┐
│ POST /plans/{id}/transitions │─────▶│ state LOCKED → RECONCILED  │
│ body { to: "RECONCILED" }    │      │ reconciledAt = now         │
│                              │◀─────│ audit_log append           │
│                              │      │ notification-svc:          │
│                              │      │   manager digest with      │
│                              │      │   reflection preview       │
│                              │      │   (~80 chars)              │
└──────────────────────────────┘      └────────────────────────────┘
```

### Flow 4 — Manager Review

```
┌──────────────────────────────┐
│ Manager opens                │
│ /weekly-commit/team          │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────────────┐    ┌────────────────────────────────┐
│ UI: GET /rollup/team?managerId=&     │───▶│ alignmentPct, completionPct,   │
│   weekStart=                         │◀───│ tierDistribution,              │
│                                      │    │ unreviewedCount,               │
│ RTK Query Rollup tag:                │    │ stuckCommitCount,              │
│   keepUnusedDataFor: 60              │    │ members: [{ id, name, state,   │
│   refetchOnFocus: true               │    │   topRock, tierCounts,         │
│                                      │    │   reflectionPreview,           │
│                                      │    │   flags[] }]                   │
│                                      │    │                                │
│                                      │    │ P95 < 500ms for 50 reports     │
└──────────┬───────────────────────────┘    └────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│ <TeamRollup /> renders:              │
│   flagged members first              │
│   on-track members below             │
│                                      │
│ <MemberCard /> shows:                │
│   name, Top Rock (or "no Top Rock"   │
│     flag), tier shape,               │
│   reflection preview (~80 chars),    │
│   flag markers (UNREVIEWED_72H /     │
│     DRAFT_WITH_UNLINKED /            │
│     STUCK_COMMIT / NO_TOP_ROCK)      │
└──────────┬───────────────────────────┘
           │  click member
           ▼
┌──────────────────────────────────────┐    ┌────────────────────────────────┐
│ <IcDrawer /> overlay                 │───▶│ GET /plans/{planId}/commits    │
│                                      │◀───│ full commit list               │
│ GET full plan, commits, audit,       │───▶│ GET /audit/plans/{planId}      │
│ full reflection                      │◀───│ audit history                  │
│                                      │    │ commit history shows streak    │
│                                      │    │ chains via carriedForwardFrom  │
│                                      │    │                                │
│ Manager types comment                │───▶│ POST /plans/{id}/reviews       │
│ clicks [Acknowledge]                 │◀───│ { comment, acknowledgedAt }    │
│                                      │    │ sets managerReviewedAt on plan │
│                                      │    │ audit_log MANAGER_REVIEW row   │
└──────────────────────────────────────┘    └────────────────────────────────┘
                                                       │
                                                       │ no approval gate
                                                       │ review is visibility-only
                                                       ▼
If reviewedAt still null 72h after reconciledAt:
  Monday 09:00 UTC digest job (Shedlock) → skip-level via notification-svc
  dashboard flag fires on the card
```

## API Endpoints

Full list in [TECH_STACK.md](TECH_STACK.md#api-endpoints-summary). Request/response shapes below show the contract for the surfaces that drive the UI most frequently.

### `GET /plans/me/current`

Returns current-week plan for the JWT subject.

**Response 200**
```json
{
  "data": {
    "id": "f3b5...",
    "employeeId": "a7c8...",
    "weekStart": "2026-04-27",
    "state": "DRAFT",
    "lockedAt": null,
    "reconciledAt": null,
    "managerReviewedAt": null,
    "reflectionNote": null,
    "version": 3
  },
  "meta": { "now": "2026-04-27T14:23:01Z", "reconciliationUnlocksAt": "2026-05-01T00:00:00Z" }
}
```

**Response 404** when no plan exists for the current week (triggers blank state).

### `POST /plans`

Idempotent create on `(employeeId, weekStart)`. No body required; server derives both from JWT + today.

**Response 201**
```json
{
  "data": { "id": "f3b5...", "state": "DRAFT", "weekStart": "2026-04-27", "version": 0 }
}
```

### `POST /plans/{id}/transitions`

```json
{ "to": "LOCKED" }
```
or
```json
{ "to": "RECONCILED" }
```

**Response 200** — updated plan.
**Response 409** — `@Version` conflict; UI refetches and toasts.
**Response 422** — guard violation (e.g. `DRAFT → RECONCILED` not allowed).

### `GET /plans/{planId}/commits`

**Response 200**
```json
{
  "data": [
    {
      "id": "c1...",
      "title": "Land RCDO picker spike",
      "description": null,
      "supportingOutcomeId": "so-44...",
      "supportingOutcome": {
        "id": "so-44...",
        "label": "Alignment tooling GA",
        "breadcrumb": "Growth › Product-led GTM › Tooling readiness › Alignment tooling GA"
      },
      "chessTier": "ROCK",
      "categoryTags": ["spike", "infra"],
      "estimatedHours": 4.0,
      "displayOrder": 0,
      "relatedMeeting": "Tues 10am alignment sync",
      "carriedForwardFromId": null,
      "carriedForwardToId": null,
      "actualStatus": "PENDING",
      "actualNote": null,
      "derived": {
        "carryStreak": 1,
        "stuckFlag": false
      }
    }
  ]
}
```

### `GET /rollup/team?managerId=&weekStart=`

**Response 200**
```json
{
  "data": {
    "alignmentPct": 0.91,
    "completionPct": 0.76,
    "tierDistribution": { "ROCK": 14, "PEBBLE": 39, "SAND": 22 },
    "unreviewedCount": 3,
    "stuckCommitCount": 2,
    "members": [
      {
        "employeeId": "e1...",
        "name": "Ada",
        "planState": "RECONCILED",
        "topRock": { "commitId": "c1...", "title": "Land RCDO picker spike" },
        "tierCounts": { "ROCK": 2, "PEBBLE": 3, "SAND": 1 },
        "reflectionPreview": "Unblocked the picker spike; the WireMock contract drift took…",
        "flags": ["UNREVIEWED_72H"]
      }
    ],
    "byOutcome": [
      { "supportingOutcomeId": "so-44...", "commitCount": 7, "completionPct": 0.71 }
    ]
  }
}
```

### `POST /plans/{id}/reviews`

```json
{ "comment": "Nice work on the picker spike — let's pair on the Flowbite token override Thursday." }
```

**Response 201** — review row, plus side-effect of `managerReviewedAt` being set on the plan.

## Example Queries

| Query | Expected Result | Expected Answer (plain-language) |
|---|---|---|
| `GET /plans/me/current` when no plan exists | HTTP 404 | "You haven't started this week — click Create plan." |
| `GET /plans/me/current` on Monday after plan created | HTTP 200, state=DRAFT | Editor mode with commit CRUD available |
| `GET /plans/me/current` on Friday afternoon of a locked week | HTTP 200, state=LOCKED, meta.reconciliationUnlocksAt in the past | Reconciliation mode opens in UI |
| `POST /plans/{id}/transitions { to: "LOCKED" }` while in DRAFT | HTTP 200 | Week locks, notification fires synchronously |
| `POST /plans/{id}/transitions { to: "RECONCILED" }` before day-4 | HTTP 422 | "Reconciliation opens Friday" |
| `POST /plans/{id}/transitions { to: "LOCKED" }` with stale version | HTTP 409 | UI toasts and refetches |
| `GET /rollup/team` for a manager with no direct reports | HTTP 200 with empty `members[]` | "No direct reports" empty state |
| `GET /rollup/team` for a manager with null-manager reports | HTTP 200 excluding them | They appear in admin's unassigned-employees report |
| `POST /commits/{id}/carry-forward` from a RECONCILED plan | HTTP 201 (twin in next week's DRAFT) | Next week's editor shows carried-forward row with CarryStreakBadge |
| `POST /commits/{id}/carry-forward` from a plan still in DRAFT | HTTP 422 | "Carry-forward is available during reconciliation" |
| `GET /audit/plans/{id}` as the plan's IC | HTTP 200 with full audit | Full transition history |
| `GET /audit/plans/{id}` as an unrelated IC | HTTP 403 | Access denied |
| `GET /audit/plans/{id}` as MANAGER of the plan's IC | HTTP 200 | Audit visible |
| `POST /admin/notifications/dlt/{id}/replay` as ADMIN | HTTP 202 | Notification requeued; DLT row deleted on success |
| Commit linked to an RCDO Supporting Outcome that RCDO later deleted | Query returns commit with `supportingOutcome` null; UI shows "outcome removed" chip | Commit preserved; manager flag surfaces |
| IC with `manager_id=null` loads team view | HTTP 200 empty; admin report lists them | Fallback is admin intervention, not a crash |
