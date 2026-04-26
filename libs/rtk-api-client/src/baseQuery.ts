import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { BaseQueryFn, FetchArgs, FetchBaseQueryError } from '@reduxjs/toolkit/query/react';

const inner = fetchBaseQuery({
  baseUrl: '',
  prepareHeaders: (headers) => {
    headers.set('accept', 'application/json');
    return headers;
  },
});

interface EnvelopeSuccess<T> { data: T; meta?: Record<string, unknown> }
interface EnvelopeError { error: { code: string; message: string; details?: unknown }; meta?: Record<string, unknown> }

export const rawBaseQuery: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (
  args,
  api,
  extraOptions,
) => {
  const result = await inner(args, api, extraOptions);

  if (result.error) {
    const body = result.error.data as Partial<EnvelopeError> | undefined;
    if (body?.error) {
      return { error: { status: result.error.status, data: body.error } as FetchBaseQueryError };
    }
    return result;
  }

  const body = result.data as Partial<EnvelopeSuccess<unknown>> | undefined;
  if (body && 'data' in body) {
    return { data: body.data };
  }
  return { data: body };
};
