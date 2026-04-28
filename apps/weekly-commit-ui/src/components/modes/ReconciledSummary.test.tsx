import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';
import { ReconciledSummary } from './ReconciledSummary';

// ReconciledSummary fires `useListCommitsQuery` and `useCarryForwardMutation`.
// Mock both directly. Same shape as the other mode tests; no MSW.
const mockUseListCommitsQuery = vi.hoisted(() => vi.fn());
const mockCarryForwardTrigger = vi.hoisted(() => vi.fn());
const mockUseCarryForwardMutation = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useListCommitsQuery: mockUseListCommitsQuery,
    useCarryForwardMutation: mockUseCarryForwardMutation,
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
    actualStatus: 'DONE',
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

describe('<ReconciledSummary />', () => {
  beforeEach(() => {
    mockUseListCommitsQuery.mockReset();
    mockCarryForwardTrigger.mockReset();
    mockUseCarryForwardMutation.mockReset();
    mockCarryForwardTrigger.mockReturnValue({ unwrap: () => Promise.resolve({}) });
    mockUseCarryForwardMutation.mockReturnValue([
      mockCarryForwardTrigger,
      { isLoading: false, error: undefined },
    ]);
  });

  it('renders the loading sentinel while commits are in flight', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
      isFetching: true,
    });
    renderWithStore(<ReconciledSummary planId={PLAN_ID} />);
    expect(screen.getByTestId('reconciled-loading')).toBeInTheDocument();
  });

  it('renders the error banner when the commits query fails', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: { status: 500, data: { error: { code: 'INTERNAL' } } },
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<ReconciledSummary planId={PLAN_ID} />);
    expect(screen.getByTestId('reconciled-error')).toBeInTheDocument();
  });

  it('renders the reconciled commit list with the RECONCILED state badge', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [
        commit({ id: 'c1', title: 'Land RCDO picker', chessTier: 'ROCK', actualStatus: 'DONE' }),
        commit({ id: 'c2', title: 'Pebble work', chessTier: 'PEBBLE', actualStatus: 'PARTIAL' }),
      ],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<ReconciledSummary planId={PLAN_ID} />);
    expect(screen.getByTestId('week-editor-reconciled')).toBeInTheDocument();
    expect(screen.getByTestId('reconciled-row-c1')).toHaveTextContent('Land RCDO picker');
    expect(screen.getByTestId('reconciled-row-c2')).toHaveTextContent('Pebble work');
    // actualStatus rendered per row.
    expect(screen.getByTestId('reconciled-row-c1-status')).toHaveTextContent(/done/i);
    expect(screen.getByTestId('reconciled-row-c2-status')).toHaveTextContent(/partial/i);
  });

  it('renders the reflection block when reflectionNote is non-empty', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit()],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(
      <ReconciledSummary planId={PLAN_ID} reflectionNote="Picker spike took longer than planned" />,
    );
    const reflection = screen.getByTestId('reconciled-reflection');
    expect(reflection).toHaveTextContent(/picker spike took longer/i);
  });

  it('omits the reflection block when reflectionNote is empty or undefined', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit()],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<ReconciledSummary planId={PLAN_ID} />);
    expect(screen.queryByTestId('reconciled-reflection')).not.toBeInTheDocument();
  });

  it('treats an empty-string reflectionNote as absent (UI parity with undefined)', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit()],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<ReconciledSummary planId={PLAN_ID} reflectionNote="" />);
    expect(screen.queryByTestId('reconciled-reflection')).not.toBeInTheDocument();
  });

  it("fires carryForward when a row's carry button is clicked", async () => {
    const user = userEvent.setup();
    mockUseListCommitsQuery.mockReturnValue({
      // PARTIAL/MISSED commits get an active carry button per CarryForwardRow.
      data: [commit({ id: 'c1', actualStatus: 'PARTIAL' })],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<ReconciledSummary planId={PLAN_ID} />);
    // CarryForwardRow renders a button with role="button" + an accessible
    // name; click any "carry" button and confirm the mutation fires with
    // the right commit id.
    const carryButtons = screen.getAllByRole('button', { name: /carry/i });
    expect(carryButtons.length).toBeGreaterThan(0);
    await user.click(carryButtons[0]!);
    expect(mockCarryForwardTrigger).toHaveBeenCalled();
    const callArg = mockCarryForwardTrigger.mock.calls[0]?.[0] as { commitId: string } | undefined;
    expect(callArg?.commitId).toBe('c1');
  });
});
