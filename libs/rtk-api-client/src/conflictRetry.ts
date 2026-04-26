import type { BaseQueryFn, FetchBaseQueryError, FetchArgs } from '@reduxjs/toolkit/query/react';
import { DEFAULT_CONFLICT_CODE, conflictToastActions } from './conflictToastSlice';

export function withConflictRetry(
  inner: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError>,
): BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> {
  return async (args, api, extraOptions) => {
    const first = await inner(args, api, extraOptions);
    if (first.error?.status !== 409) {
      return first;
    }
    const code = (first.error.data as { code?: string } | undefined)?.code ?? DEFAULT_CONFLICT_CODE;
    api.dispatch(conflictToastActions.show({ code }));
    const second = await inner(args, api, extraOptions);
    return second;
  };
}
