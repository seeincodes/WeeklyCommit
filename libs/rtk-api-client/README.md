# @wc/rtk-api-client

Typed RTK Query hooks for the Weekly Commit backend. All 17 endpoints from [libs/contracts/openapi.yaml](../contracts/openapi.yaml) are exposed as React hooks, with per-endpoint tag invalidation aligned to PRD MVP9 (Rollup freshness ≤60s + refetchOnFocus) and the optimistic-locking contract (MEMO decision #4).

## Public surface

```ts
import {
  // The api slice itself + config + tags
  api, API_CONFIG, TAGS, type Tag,

  // 17 React hooks (3 query, 14 query/mutation)
  useGetCurrentForMeQuery,
  useGetPlanByEmployeeAndWeekQuery,
  useCreateCurrentForMeMutation,
  useTransitionMutation,
  useUpdateReflectionMutation,
  useListCommitsQuery,
  useCreateCommitMutation,
  useUpdateCommitMutation,
  useDeleteCommitMutation,
  useCarryForwardMutation,
  useListReviewsQuery,
  useCreateReviewMutation,
  useGetTeamRollupQuery,
  useGetTeamQuery,
  useGetAuditForPlanQuery,
  useListUnassignedEmployeesQuery,
  useReplayDltRowMutation,

  // Conflict-toast slice (consumed by group 11's <ConflictToast />)
  conflictToastSlice,
  conflictToastActions,
  selectConflictToast,
  DEFAULT_CONFLICT_CODE,
  type ConflictToastState,

  // Narrow response/request type aliases over openapi-typescript
  type WeeklyPlanResponse,
  type WeeklyCommitResponse,
  type ManagerReviewResponse,
  type RollupResponse,
  type MemberCard,
  type AuditLogResponse,
  type UnassignedEmployeeResponse,
  type CreateCommitRequest,
  type UpdateCommitRequest,
  type CreateReviewRequest,
  type TransitionRequest,
  type UpdateReflectionRequest,
} from '@wc/rtk-api-client';
```

## Tag invalidation cheatsheet

| Tag | Provided by | Invalidated by |
|---|---|---|
| `Plan-{id}` | getCurrentForMe, getPlanByEmployeeAndWeek | transition, updateReflection, updateCommit (via response.planId), createReview |
| `Plan-LIST` | getTeam | createCurrentForMe, deleteCommit |
| `Commit-{id}` | listCommits (per row) | updateCommit |
| `Commit-LIST` | listCommits | createCommit, deleteCommit, carryForward, transition |
| `Review-{planId}` | listReviews | createReview |
| `Rollup-LIST` | getTeamRollup | createCurrentForMe, createReview |
| `Audit-{id}` | getAuditForPlan | transition |
| `RCDO` | (group 11 — RCDOPicker) | (none — read-only upstream) |

## 409 conflict handling

When the server returns HTTP 409 (`OptimisticLockException` mapped in [GlobalExceptionHandler](../../apps/weekly-commit-service/src/main/java/com/acme/weeklycommit/api/exception/GlobalExceptionHandler.java) → `CONFLICT_OPTIMISTIC_LOCK`), the baseQuery enhancer:

1. Dispatches `conflictToastActions.show({ code: 'CONFLICT_OPTIMISTIC_LOCK' })`.
2. Retries the request once (the upstream cache may have been refetched between attempts via tag invalidation).
3. Returns the second response — clean data on success, propagated error on a second 409.

This is wired as a `baseQuery` enhancer rather than Redux middleware so the lifecycle sees a clean response, avoiding a flicker through a rejected state. Group 11 wires the actual `<ConflictToast />` UI component to the slice via `selectConflictToast`.

## Adding a new endpoint

The pattern is established by the existing 17 endpoints. For each addition:

1. Add an MSW-driven test in [src/__tests__/api.test.ts](src/__tests__/api.test.ts) asserting URL + method + body + (for mutations) tag invalidation behavior.
2. Add the endpoint definition in [src/api.ts](src/api.ts) with the right `providesTags` / `invalidatesTags`. Use the type aliases from [src/types.ts](src/types.ts) — they narrow the optional-everywhere openapi-typescript shapes to what the server actually guarantees.
3. Export the auto-generated hook in [src/index.ts](src/index.ts).
4. `yarn workspace @wc/rtk-api-client test` — must pass.

Run-locally invariants:
- `yarn workspace @wc/rtk-api-client lint` — silent.
- `yarn workspace @wc/rtk-api-client typecheck` — silent.
- `yarn workspace @wc/rtk-api-client test:unit` — coverage ≥ 80% on lines/statements/branches/functions per [docs/TESTING_STRATEGY.md](../../docs/TESTING_STRATEGY.md).

## Drift checking

The OpenAPI types come from [@wc/contracts](../contracts/). Re-run `yarn workspace @wc/contracts verify:ts` after any backend OpenAPI change; CI fails on drift. After any URL / operationId / request-body change, **also** update the corresponding hand-written endpoint definition in [src/api.ts](src/api.ts) and its test — the OpenAPI spec is the contract, but the hook wiring is hand-maintained.

## Why hand-written endpoints (vs. `@rtk-query/codegen-openapi`)

Codegen can't express per-endpoint tag invalidation policy, which is the actual reason RTK Query exists. Each endpoint here:

- Pulls request/response shapes from `operations["operationId"]` so the openapi-typescript output is the single source of truth.
- Sets explicit `providesTags` / `invalidatesTags` based on the domain's cache topology (reads of `WeeklyPlan` provide `Plan-{id}`, mutations on commits invalidate the parent plan + the commit list, etc.).

We will reconsider codegen if the endpoint count exceeds ~40 and the tag wiring becomes mechanical. Today there are 17.
