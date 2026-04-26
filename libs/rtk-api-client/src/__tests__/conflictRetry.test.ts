import { describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './setup';
import { withConflictRetry } from '../conflictRetry';
import { rawBaseQuery } from '../baseQuery';

const mkApi = (dispatch = vi.fn()) => ({
  signal: new AbortController().signal,
  abort: () => undefined,
  dispatch,
  getState: () => ({}),
  extra: undefined,
  endpoint: 'test',
  type: 'mutation' as const,
  forced: false,
});

describe('withConflictRetry', () => {
  it('retries once on HTTP 409 and returns the second response', async () => {
    let calls = 0;
    server.use(
      http.post('http://localhost/api/v1/plans/p1/transitions', () => {
        calls += 1;
        if (calls === 1) {
          return HttpResponse.json(
            { error: { code: 'CONFLICT_OPTIMISTIC_LOCK', message: 'stale' }, meta: {} },
            { status: 409 },
          );
        }
        return HttpResponse.json({ data: { id: 'p1', state: 'LOCKED' }, meta: {} });
      }),
    );
    const wrapped = withConflictRetry(rawBaseQuery);
    const result = await wrapped(
      { url: 'http://localhost/api/v1/plans/p1/transitions', method: 'POST', body: { to: 'LOCKED' } },
      mkApi(),
      {},
    );
    expect(calls).toBe(2);
    expect(result).toEqual({ data: { id: 'p1', state: 'LOCKED' } });
  });

  it('dispatches conflictToast/show with the conflict code on 409', async () => {
    server.use(
      http.post('http://localhost/api/v1/plans/p1/transitions', () => {
        return HttpResponse.json(
          { error: { code: 'CONFLICT_OPTIMISTIC_LOCK', message: 'stale' }, meta: {} },
          { status: 409 },
        );
      }),
      // Second attempt also 409 to keep the test focused on the dispatch side-effect.
    );
    const dispatch = vi.fn();
    const wrapped = withConflictRetry(rawBaseQuery);
    await wrapped(
      { url: 'http://localhost/api/v1/plans/p1/transitions', method: 'POST', body: { to: 'LOCKED' } },
      mkApi(dispatch),
      {},
    );
    expect(dispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'conflictToast/show',
        payload: { code: 'CONFLICT_OPTIMISTIC_LOCK' },
      }),
    );
  });

  it('passes through non-409 errors without retry or dispatch', async () => {
    let calls = 0;
    server.use(
      http.post('http://localhost/api/v1/plans/p1/transitions', () => {
        calls += 1;
        return HttpResponse.json(
          { error: { code: 'INVALID_STATE_TRANSITION', message: 'guard failed' }, meta: {} },
          { status: 422 },
        );
      }),
    );
    const dispatch = vi.fn();
    const wrapped = withConflictRetry(rawBaseQuery);
    const result = await wrapped(
      { url: 'http://localhost/api/v1/plans/p1/transitions', method: 'POST', body: { to: 'LOCKED' } },
      mkApi(dispatch),
      {},
    );
    expect(calls).toBe(1);
    expect(dispatch).not.toHaveBeenCalled();
    expect(result.error).toMatchObject({ status: 422, data: { code: 'INVALID_STATE_TRANSITION' } });
  });
});
