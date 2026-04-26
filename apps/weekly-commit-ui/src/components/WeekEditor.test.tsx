import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import { draftFormSlice } from '../store/draftFormSlice';
import { WeekEditor } from './WeekEditor';
import type { WeeklyPlanResponse } from '@wc/rtk-api-client';

const mockUseGetCurrentForMeQuery = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useGetCurrentForMeQuery: mockUseGetCurrentForMeQuery,
  };
});

function renderWithStore() {
  // Real store -- the api slice's reducer needs to exist even though we mock the hook,
  // because <Provider> children may dispatch other api actions. Cheaper than building a
  // hand-rolled store stub and avoids drift from the real store shape.
  const store = configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
      draftForm: draftFormSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  return render(
    <Provider store={store}>
      <WeekEditor now={new Date('2026-04-29T12:00:00Z')} />
    </Provider>,
  );
}

function plan(overrides: Partial<WeeklyPlanResponse> = {}): WeeklyPlanResponse {
  return {
    id: 'plan-1',
    employeeId: 'emp-1',
    weekStart: '2026-04-27', // Monday
    state: 'DRAFT',
    version: 0,
    ...overrides,
  };
}

describe('<WeekEditor />', () => {
  beforeEach(() => {
    mockUseGetCurrentForMeQuery.mockReset();
  });

  it('shows the loading state while the plan query is in flight', () => {
    mockUseGetCurrentForMeQuery.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
      isFetching: true,
    });
    renderWithStore();
    expect(screen.getByTestId('week-editor-loading')).toBeInTheDocument();
  });

  it('shows the blank state on a 404 (no plan exists for this week)', () => {
    mockUseGetCurrentForMeQuery.mockReturnValue({
      data: undefined,
      error: { status: 404 },
      isLoading: false,
      isFetching: false,
    });
    renderWithStore();
    expect(screen.getByTestId('week-editor-blank')).toBeInTheDocument();
    // Per MEMO decision #10 the user creates the plan with an explicit click.
    expect(screen.getByRole('button', { name: /create plan/i })).toBeInTheDocument();
  });

  it('shows the draft editor for state=DRAFT', () => {
    mockUseGetCurrentForMeQuery.mockReturnValue({
      data: plan({ state: 'DRAFT' }),
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore();
    expect(screen.getByTestId('week-editor-draft')).toBeInTheDocument();
  });

  it('shows the locked read-only view for state=LOCKED before weekStart + 4 days', () => {
    // weekStart 2026-04-27, now = 2026-04-29 (Wed) -> 2 days in
    mockUseGetCurrentForMeQuery.mockReturnValue({
      data: plan({ state: 'LOCKED', weekStart: '2026-04-27' }),
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore();
    expect(screen.getByTestId('week-editor-locked-readonly')).toBeInTheDocument();
  });

  it('shows reconciliation mode for state=LOCKED at or after weekStart + 4 days', () => {
    // weekStart 2026-04-27, now = 2026-05-01 (Fri 00:00Z) -> exactly +4d, threshold met.
    mockUseGetCurrentForMeQuery.mockReset();
    mockUseGetCurrentForMeQuery.mockReturnValue({
      data: plan({ state: 'LOCKED', weekStart: '2026-04-27' }),
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    render(
      <Provider
        store={configureStore({
          reducer: {
            [api.reducerPath]: api.reducer,
            conflictToast: conflictToastSlice.reducer,
            draftForm: draftFormSlice.reducer,
          },
          middleware: (getDefault) => getDefault().concat(api.middleware),
        })}
      >
        <WeekEditor now={new Date('2026-05-01T00:00:00Z')} />
      </Provider>,
    );
    expect(screen.getByTestId('week-editor-reconcile')).toBeInTheDocument();
  });

  it('shows the reconciled summary for state=RECONCILED', () => {
    mockUseGetCurrentForMeQuery.mockReturnValue({
      data: plan({ state: 'RECONCILED' }),
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore();
    expect(screen.getByTestId('week-editor-reconciled')).toBeInTheDocument();
  });

  it('shows an error banner on non-404 errors', () => {
    mockUseGetCurrentForMeQuery.mockReturnValue({
      data: undefined,
      error: { status: 500, data: { error: { code: 'INTERNAL', message: 'oops' } } },
      isLoading: false,
      isFetching: false,
    });
    renderWithStore();
    expect(screen.getByTestId('week-editor-error')).toBeInTheDocument();
  });
});
