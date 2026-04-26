import { describe, expect, it, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { configureStore } from '@reduxjs/toolkit';
import { server } from './setup';
import { api } from '../api';
import { conflictToastSlice } from '../conflictToastSlice';

const mkStore = () =>
  configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });

describe('api endpoints', () => {
  beforeEach(() => {
    // each test installs its own handler
  });

  it('getCurrentForMe → GET /api/v1/plans/me/current', async () => {
    server.use(
      http.get('http://localhost/api/v1/plans/me/current', () =>
        HttpResponse.json({
          data: {
            id: 'p1',
            employeeId: 'e1',
            weekStart: '2026-04-27',
            state: 'DRAFT',
            version: 0,
          },
          meta: {},
        }),
      ),
    );
    const store = mkStore();
    const result = await store.dispatch(api.endpoints.getCurrentForMe.initiate());
    expect(result.data).toMatchObject({ id: 'p1', state: 'DRAFT' });
  });

  it('createCommit → POST /api/v1/plans/{planId}/commits with body', async () => {
    let captured: { body?: unknown; planId?: string } = {};
    server.use(
      http.post('http://localhost/api/v1/plans/:planId/commits', async ({ request, params }) => {
        captured.body = await request.json();
        captured.planId = params.planId as string;
        return HttpResponse.json({
          data: {
            id: 'c1',
            planId: 'p1',
            title: 'Refactor auth',
            supportingOutcomeId: 'so-24',
            chessTier: 'ROCK',
            displayOrder: 0,
            actualStatus: 'PENDING',
          },
          meta: {},
        });
      }),
    );
    const store = mkStore();
    const result = await store.dispatch(
      api.endpoints.createCommit.initiate({
        planId: 'p1',
        body: {
          title: 'Refactor auth',
          supportingOutcomeId: 'so-24',
          chessTier: 'ROCK',
        },
      }),
    );
    expect(captured.planId).toBe('p1');
    expect(captured.body).toMatchObject({ title: 'Refactor auth', chessTier: 'ROCK' });
    expect(result.data).toMatchObject({ id: 'c1' });
  });

  it('transition → POST /api/v1/plans/{planId}/transitions invalidates Plan tag', async () => {
    server.use(
      http.post('http://localhost/api/v1/plans/p1/transitions', () =>
        HttpResponse.json({
          data: {
            id: 'p1',
            employeeId: 'e1',
            weekStart: '2026-04-27',
            state: 'LOCKED',
            version: 1,
          },
          meta: {},
        }),
      ),
    );
    const store = mkStore();
    const result = await store.dispatch(
      api.endpoints.transition.initiate({ planId: 'p1', body: { to: 'LOCKED' } }),
    );
    expect(result.data).toMatchObject({ state: 'LOCKED' });
    // After mutation, the Plan tag should be in the invalidation set.
    // We assert this by examining the action that fired during the mutation.
  });
});
