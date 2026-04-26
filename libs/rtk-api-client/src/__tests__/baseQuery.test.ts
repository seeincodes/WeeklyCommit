import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from './setup';
import { rawBaseQuery } from '../baseQuery';

const mkApi = () => ({
  signal: new AbortController().signal,
  abort: () => undefined,
  dispatch: () => undefined,
  getState: () => ({}),
  extra: undefined,
  endpoint: 'test',
  type: 'query' as const,
  forced: false,
});

describe('rawBaseQuery', () => {
  it('unwraps the {data, meta} envelope on 2xx', async () => {
    server.use(
      http.get('http://localhost/api/v1/example', () =>
        HttpResponse.json({ data: { id: 'abc', value: 42 }, meta: { now: '2026-04-25T00:00:00Z' } }),
      ),
    );
    const result = await rawBaseQuery(
      { url: 'http://localhost/api/v1/example' },
      mkApi(),
      {},
    );
    expect(result).toEqual({ data: { id: 'abc', value: 42 } });
  });

  it('surfaces error.code as the rejection reason on 4xx', async () => {
    server.use(
      http.post('http://localhost/api/v1/example', () =>
        HttpResponse.json(
          { error: { code: 'VALIDATION_FAILED', message: 'bad' }, meta: {} },
          { status: 400 },
        ),
      ),
    );
    const result = await rawBaseQuery(
      { url: 'http://localhost/api/v1/example', method: 'POST', body: {} },
      mkApi(),
      {},
    );
    expect(result.error).toMatchObject({ status: 400, data: { code: 'VALIDATION_FAILED' } });
  });

  it('passes through network errors without trying to read the envelope', async () => {
    server.use(http.get('http://localhost/api/v1/example', () => HttpResponse.error()));
    const result = await rawBaseQuery(
      { url: 'http://localhost/api/v1/example' },
      mkApi(),
      {},
    );
    expect(result.error).toBeDefined();
    expect((result.error as { status?: unknown }).status).toBe('FETCH_ERROR');
  });
});
