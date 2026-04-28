import { createApi } from '@reduxjs/toolkit/query/react';
import { withConflictRetry } from './conflictRetry';
import { rawBaseQuery } from './baseQuery';
import type {
  AuditLogResponse,
  CreateCommitRequest,
  CreateReviewRequest,
  ManagerReviewResponse,
  RollupResponse,
  TransitionRequest,
  UnassignedEmployeeResponse,
  UpdateCommitRequest,
  UpdateReflectionRequest,
  WeeklyCommitResponse,
  WeeklyPlanResponse,
} from './types';
import type { SupportingOutcome } from './rcdo';

// `import.meta.env` is provided by Vite (and surfaced to vitest via the same
// transform). Read VITE_API_BASE_URL via a narrow inline cast so this lib
// doesn't have to add `vite/client` to its tsconfig types — and so it doesn't
// re-declare `ImportMetaEnv` in a way that would conflict with the consuming
// app's vite/client typing when typechecked across the workspace.
const env = (import.meta as ImportMeta & { env?: { VITE_API_BASE_URL?: string } }).env;
// Three modes:
//   - undefined / unset: vitest's MSW patterns target absolute http://localhost/...
//     URLs, so we fall back to "http://localhost" (no port) to match.
//   - explicit "": demo / production same-origin. The frontend is served by the
//     backend (Railway) or by a host that proxies /api/* (federated prod).
//     Relative URLs like "/api/v1/..." resolve against window.location.origin,
//     which is what we want.
//   - explicit "https://...": deployment-specific override (rare).
//
// The empty-string vs undefined distinction matters because vitest used to
// surface VITE_API_BASE_URL as "" rather than undefined when running through
// nx -- in that environment the test scaffolding's MSW URL patterns broke.
// That's been fixed upstream; the test envs now produce undefined. So we can
// treat empty string as a deliberate "use same-origin" signal, distinct from
// "not set at all."
const rawBaseUrl = env?.VITE_API_BASE_URL;
const baseUrl: string = rawBaseUrl ?? 'http://localhost';

const baseQuery = withConflictRetry(rawBaseQuery);

const baseQueryWithBaseUrl: typeof baseQuery = (args, api, extra) => {
  if (typeof args === 'string') {
    return baseQuery(`${baseUrl}${args}`, api, extra);
  }
  return baseQuery({ ...args, url: `${baseUrl}${args.url}` }, api, extra);
};

export const TAGS = ['Plan', 'Commit', 'Review', 'Rollup', 'Audit', 'RCDO'] as const;
export type Tag = (typeof TAGS)[number];

// Surfaced for test assertion; spread into createApi below. RTK does not
// re-expose api-level config on the returned slice, so we keep a named
// reference to verify the policy at unit-test time.
export const API_CONFIG = {
  refetchOnFocus: true,
  refetchOnReconnect: true,
  keepUnusedDataFor: 60,
} as const;

export const api = createApi({
  reducerPath: 'wcApi',
  baseQuery: baseQueryWithBaseUrl,
  tagTypes: [...TAGS],
  ...API_CONFIG,
  endpoints: (build) => ({
    getCurrentForMe: build.query<WeeklyPlanResponse, void>({
      query: () => '/api/v1/plans/me/current',
      providesTags: (result) => (result ? [{ type: 'Plan', id: result.id }] : ['Plan']),
    }),
    createCommit: build.mutation<
      WeeklyCommitResponse,
      { planId: string; body: CreateCommitRequest }
    >({
      query: ({ planId, body }) => ({
        url: `/api/v1/plans/${planId}/commits`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_res, _err, { planId }) => [
        { type: 'Plan', id: planId },
        { type: 'Commit', id: 'LIST' },
      ],
    }),
    transition: build.mutation<WeeklyPlanResponse, { planId: string; body: TransitionRequest }>({
      query: ({ planId, body }) => ({
        url: `/api/v1/plans/${planId}/transitions`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_res, _err, { planId }) => [
        { type: 'Plan', id: planId },
        { type: 'Commit', id: 'LIST' },
        { type: 'Audit', id: planId },
      ],
    }),
    getPlanByEmployeeAndWeek: build.query<
      WeeklyPlanResponse,
      { employeeId: string; weekStart: string }
    >({
      query: ({ employeeId, weekStart }) => ({
        url: '/api/v1/plans',
        params: { employeeId, weekStart },
      }),
      providesTags: (result) => (result ? [{ type: 'Plan', id: result.id }] : ['Plan']),
    }),
    createCurrentForMe: build.mutation<WeeklyPlanResponse, void>({
      query: () => ({
        url: '/api/v1/plans',
        method: 'POST',
      }),
      invalidatesTags: [
        { type: 'Plan', id: 'LIST' },
        { type: 'Rollup', id: 'LIST' },
      ],
      // Perf: write the new plan directly into `getCurrentForMe`'s cache so
      // BlankState -> DraftMode doesn't need a refetch round-trip after the
      // POST resolves. Without this, the chain is:
      //   POST /plans -> 200ms tag-invalidation refetch -> GET /plans/me/current
      //   -> rerender -> mount DraftMode -> GET /commits.
      // With this, we drop the `GET /plans/me/current` round-trip; the cache
      // already has the row and RTK Query's selector reads it synchronously.
      // Tag invalidation still fires (invalidatesTags above) so anything else
      // listening on Plan / Rollup tags refetches as expected.
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data: newPlan } = await queryFulfilled;
          // upsertQueryData is a thunk; dispatching it returns a Promise we
          // intentionally fire-and-forget. The cache write is synchronous in
          // practice and any failure is irrelevant to user-visible flow --
          // tag invalidation will still trigger a refetch as a fallback.
          void dispatch(api.util.upsertQueryData('getCurrentForMe', undefined, newPlan));
        } catch {
          // Server rejected the create -- let the error bubble through the
          // mutation's normal error path; no cache write to revert.
        }
      },
    }),
    updateReflection: build.mutation<
      WeeklyPlanResponse,
      { planId: string; body: UpdateReflectionRequest }
    >({
      query: ({ planId, body }) => ({
        url: `/api/v1/plans/${planId}`,
        method: 'PATCH',
        body,
      }),
      invalidatesTags: (_res, _err, { planId }) => [{ type: 'Plan', id: planId }],
    }),
    getTeam: build.query<WeeklyPlanResponse[], { managerId: string; weekStart: string }>({
      query: ({ managerId, weekStart }) => ({
        url: '/api/v1/plans/team',
        params: { managerId, weekStart },
      }),
      providesTags: [{ type: 'Plan', id: 'LIST' }],
      keepUnusedDataFor: 60,
    }),
    listCommits: build.query<WeeklyCommitResponse[], { planId: string }>({
      query: ({ planId }) => `/api/v1/plans/${planId}/commits`,
      providesTags: (result) =>
        result
          ? [
              ...result.map((c) => ({ type: 'Commit' as const, id: c.id })),
              { type: 'Commit', id: 'LIST' },
            ]
          : [{ type: 'Commit', id: 'LIST' }],
    }),
    updateCommit: build.mutation<
      WeeklyCommitResponse,
      { commitId: string; body: UpdateCommitRequest }
    >({
      query: ({ commitId, body }) => ({
        url: `/api/v1/commits/${commitId}`,
        method: 'PATCH',
        body,
      }),
      invalidatesTags: (result, _err, { commitId }) =>
        result
          ? [
              { type: 'Commit', id: commitId },
              { type: 'Plan', id: result.planId },
            ]
          : [{ type: 'Commit', id: commitId }],
    }),
    deleteCommit: build.mutation<void, { commitId: string }>({
      query: ({ commitId }) => ({
        url: `/api/v1/commits/${commitId}`,
        method: 'DELETE',
      }),
      invalidatesTags: [
        { type: 'Commit', id: 'LIST' },
        { type: 'Plan', id: 'LIST' },
      ],
    }),
    carryForward: build.mutation<WeeklyCommitResponse, { commitId: string }>({
      query: ({ commitId }) => ({
        url: `/api/v1/commits/${commitId}/carry-forward`,
        method: 'POST',
      }),
      invalidatesTags: [{ type: 'Commit', id: 'LIST' }],
    }),
    listReviews: build.query<ManagerReviewResponse[], { planId: string }>({
      query: ({ planId }) => `/api/v1/plans/${planId}/reviews`,
      providesTags: (_res, _err, { planId }) => [{ type: 'Review', id: planId }],
    }),
    createReview: build.mutation<
      ManagerReviewResponse,
      { planId: string; body: CreateReviewRequest }
    >({
      query: ({ planId, body }) => ({
        url: `/api/v1/plans/${planId}/reviews`,
        method: 'POST',
        body,
      }),
      invalidatesTags: (_res, _err, { planId }) => [
        { type: 'Review', id: planId },
        { type: 'Plan', id: planId },
        { type: 'Rollup', id: 'LIST' },
      ],
    }),
    getTeamRollup: build.query<RollupResponse, { managerId: string; weekStart: string }>({
      query: ({ managerId, weekStart }) => ({
        url: '/api/v1/rollup/team',
        params: { managerId, weekStart },
      }),
      providesTags: [{ type: 'Rollup', id: 'LIST' }],
      keepUnusedDataFor: 60,
    }),
    getAuditForPlan: build.query<AuditLogResponse[], { id: string }>({
      query: ({ id }) => `/api/v1/audit/plans/${id}`,
      providesTags: (_res, _err, { id }) => [{ type: 'Audit', id }],
    }),
    listUnassignedEmployees: build.query<UnassignedEmployeeResponse[], void>({
      query: () => '/api/v1/admin/unassigned-employees',
    }),
    replayDltRow: build.mutation<void, { id: string }>({
      query: ({ id }) => ({
        url: `/api/v1/admin/notifications/dlt/${id}/replay`,
        method: 'POST',
      }),
    }),
    // RCDO pass-through (group 11a). Backed by the new BE controller that wraps the existing
    // backend RcdoClient. Both endpoints get the 10-minute cache from MEMO #6 -- the picker is
    // typically opened once per planning session, and the hierarchy doesn't change minute-to-
    // minute upstream.
    getSupportingOutcomes: build.query<SupportingOutcome[], void>({
      query: () => '/api/v1/rcdo/supporting-outcomes',
      providesTags: ['RCDO'],
      keepUnusedDataFor: 600,
    }),
    getSupportingOutcomeById: build.query<SupportingOutcome, { id: string }>({
      query: ({ id }) => `/api/v1/rcdo/supporting-outcomes/${id}`,
      providesTags: (_res, _err, { id }) => [{ type: 'RCDO', id }],
      keepUnusedDataFor: 600,
    }),
  }),
});

export const {
  useGetCurrentForMeQuery,
  useCreateCommitMutation,
  useTransitionMutation,
  useGetPlanByEmployeeAndWeekQuery,
  useCreateCurrentForMeMutation,
  useUpdateReflectionMutation,
  useGetTeamQuery,
  useListCommitsQuery,
  useUpdateCommitMutation,
  useDeleteCommitMutation,
  useCarryForwardMutation,
  useListReviewsQuery,
  useCreateReviewMutation,
  useGetTeamRollupQuery,
  useGetAuditForPlanQuery,
  useListUnassignedEmployeesQuery,
  useReplayDltRowMutation,
  useGetSupportingOutcomesQuery,
  useGetSupportingOutcomeByIdQuery,
} = api;
