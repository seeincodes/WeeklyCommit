import { createApi } from '@reduxjs/toolkit/query/react';
import { withConflictRetry } from './conflictRetry';
import { rawBaseQuery } from './baseQuery';
import type {
  CreateCommitRequest,
  TransitionRequest,
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
  }),
});

export const { useGetCurrentForMeQuery, useCreateCommitMutation, useTransitionMutation } = api;
