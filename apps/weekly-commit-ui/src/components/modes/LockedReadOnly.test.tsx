import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';
import { LockedReadOnly } from './LockedReadOnly';

// LockedReadOnly only fires `useListCommitsQuery`. Mock that one hook
// directly, same shape as DraftMode/ReconcileMode tests.
const mockUseListCommitsQuery = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useListCommitsQuery: mockUseListCommitsQuery,
  };
});

const PLAN_ID = '99999999-9999-9999-9999-999999999999';

function commit(overrides: Partial<WeeklyCommitResponse> = {}): WeeklyCommitResponse {
  return {
    id: 'c1',
    planId: PLAN_ID,
    title: 'Ship picker',
    supportingOutcomeId: 'so-1',
    chessTier: 'PEBBLE',
    displayOrder: 1,
    actualStatus: 'PENDING',
    ...overrides,
  };
}

function renderWithStore(ui: React.ReactNode) {
  const store = configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  return render(<Provider store={store}>{ui}</Provider>);
}

describe('<LockedReadOnly />', () => {
  beforeEach(() => {
    mockUseListCommitsQuery.mockReset();
  });

  it('renders the loading sentinel while commits are in flight', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
      isFetching: true,
    });
    renderWithStore(<LockedReadOnly planId={PLAN_ID} />);
    expect(screen.getByTestId('locked-readonly-loading')).toBeInTheDocument();
  });

  it('renders the error banner when the commits query fails', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: { status: 500, data: { error: { code: 'INTERNAL' } } },
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<LockedReadOnly planId={PLAN_ID} />);
    expect(screen.getByTestId('locked-readonly-error')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/couldn.{1,3}t load/i);
  });

  it('renders the read-only commit list with the LOCKED state badge', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [
        commit({ id: 'c1', title: 'Land RCDO picker', chessTier: 'ROCK' }),
        commit({ id: 'c2', title: 'Pebble work', chessTier: 'PEBBLE' }),
      ],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<LockedReadOnly planId={PLAN_ID} />);
    expect(screen.getByTestId('week-editor-locked-readonly')).toBeInTheDocument();
    // Both commits visible in their tier groups.
    expect(screen.getByTestId('locked-row-c1')).toHaveTextContent('Land RCDO picker');
    expect(screen.getByTestId('locked-row-c2')).toHaveTextContent('Pebble work');
  });

  it("surfaces each commit's actualStatus next to its title", () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit({ id: 'c1', actualStatus: 'PENDING' })],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<LockedReadOnly planId={PLAN_ID} />);
    expect(screen.getByTestId('locked-row-c1-status')).toHaveTextContent(/pending/i);
  });

  it('handles an empty commits array without crashing', () => {
    // Edge case: a plan transitioned to LOCKED with no commits would be a bug
    // upstream, but the mode shouldn't blow up if it ever happens.
    mockUseListCommitsQuery.mockReturnValue({
      data: [],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<LockedReadOnly planId={PLAN_ID} />);
    expect(screen.getByTestId('week-editor-locked-readonly')).toBeInTheDocument();
  });
});
