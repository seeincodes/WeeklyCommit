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

interface ViteEnv {
  VITE_API_BASE_URL?: string;
}
interface ViteImportMeta extends ImportMeta {
  env: ViteEnv;
}
const baseUrl: string = (import.meta as ViteImportMeta).env.VITE_API_BASE_URL ?? 'http://localhost';

const baseQuery = withConflictRetry(rawBaseQuery);

const baseQueryWithBaseUrl: typeof baseQuery = (args, api, extra) => {
  if (typeof args === 'string') {
    return baseQuery(`${baseUrl}${args}`, api, extra);
  }
  return baseQuery({ ...args, url: `${baseUrl}${args.url}` }, api, extra);
};

export const TAGS = ['Plan', 'Commit', 'Review', 'Rollup', 'Audit', 'RCDO'] as const;
export type Tag = (typeof TAGS)[number];

export const api = createApi({
  reducerPath: 'wcApi',
  baseQuery: baseQueryWithBaseUrl,
  tagTypes: [...TAGS],
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
    getTeamRollup: build.query<
      RollupResponse,
      { managerId: string; weekStart: string }
    >({
      query: ({ managerId, weekStart }) => ({
        url: '/api/v1/rollup/team',
        params: { managerId, weekStart },
      }),
      providesTags: [{ type: 'Rollup', id: 'LIST' }],
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
} = api;
