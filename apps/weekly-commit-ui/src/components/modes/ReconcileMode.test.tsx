import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';
import { ReconcileMode } from './ReconcileMode';

// No shared MSW server in this codebase; mock the RTK Query hooks directly,
// matching the established DraftMode.test.tsx + WeekEditor.test.tsx pattern.
// Each mutation hook returns the `[trigger, status]` tuple RTK Query exposes.
const mockUseListCommitsQuery = vi.hoisted(() => vi.fn());
const mockUpdateCommitTrigger = vi.hoisted(() => vi.fn());
const mockUseUpdateCommitMutation = vi.hoisted(() => vi.fn());
const mockUpdateReflectionTrigger = vi.hoisted(() => vi.fn());
const mockUseUpdateReflectionMutation = vi.hoisted(() => vi.fn());
const mockTransitionTrigger = vi.hoisted(() => vi.fn());
const mockUseTransitionMutation = vi.hoisted(() => vi.fn());
const mockCarryForwardTrigger = vi.hoisted(() => vi.fn());
const mockUseCarryForwardMutation = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useListCommitsQuery: mockUseListCommitsQuery,
    useUpdateCommitMutation: mockUseUpdateCommitMutation,
    useUpdateReflectionMutation: mockUseUpdateReflectionMutation,
    useTransitionMutation: mockUseTransitionMutation,
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

describe('<ReconcileMode />', () => {
  beforeEach(() => {
    mockUseListCommitsQuery.mockReset();
    mockUpdateCommitTrigger.mockReset();
    mockUseUpdateCommitMutation.mockReset();
    mockUpdateReflectionTrigger.mockReset();
    mockUseUpdateReflectionMutation.mockReset();
    mockTransitionTrigger.mockReset();
    mockUseTransitionMutation.mockReset();
    mockCarryForwardTrigger.mockReset();
    mockUseCarryForwardMutation.mockReset();

    mockUseListCommitsQuery.mockReturnValue({
      data: [commit()],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    mockUpdateCommitTrigger.mockReturnValue({ unwrap: () => Promise.resolve(commit()) });
    mockUseUpdateCommitMutation.mockReturnValue([
      mockUpdateCommitTrigger,
      { isLoading: false, error: undefined },
    ]);
    mockUpdateReflectionTrigger.mockReturnValue({ unwrap: () => Promise.resolve({}) });
    mockUseUpdateReflectionMutation.mockReturnValue([
      mockUpdateReflectionTrigger,
      { isLoading: false, error: undefined },
    ]);
    mockTransitionTrigger.mockReturnValue({ unwrap: () => Promise.resolve({}) });
    mockUseTransitionMutation.mockReturnValue([
      mockTransitionTrigger,
      { isLoading: false, error: undefined },
    ]);
    mockCarryForwardTrigger.mockReturnValue({ unwrap: () => Promise.resolve({}) });
    mockUseCarryForwardMutation.mockReturnValue([
      mockCarryForwardTrigger,
      { isLoading: false, error: undefined },
    ]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the loading sentinel while commits are in flight', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
      isFetching: true,
    });
    renderWithStore(<ReconcileMode planId={PLAN_ID} />);
    expect(screen.getByTestId('reconcile-loading')).toBeInTheDocument();
  });

  it('renders the error sentinel when the commits query errors', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: { status: 500 },
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<ReconcileMode planId={PLAN_ID} />);
    expect(screen.getByTestId('reconcile-error')).toBeInTheDocument();
  });

  it('renders the StateBadge, the ReconcileTable, the ReflectionField, and the Submit button', () => {
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    expect(screen.getByTestId('week-editor-reconcile')).toBeInTheDocument();
    expect(screen.getByTestId('state-badge')).toHaveAttribute('data-state', 'LOCKED');
    // ReconcileTable renders a row per commit with DONE/PARTIAL/MISSED radios.
    expect(screen.getByRole('radio', { name: /done/i })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /reflection/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /submit reconciliation/i })).toBeInTheDocument();
  });

  it('PATCHes a commit row when its actualStatus radio changes', async () => {
    const user = userEvent.setup();
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    await user.click(screen.getByRole('radio', { name: /done/i }));

    await waitFor(() => expect(mockUpdateCommitTrigger).toHaveBeenCalledTimes(1));
    expect(mockUpdateCommitTrigger).toHaveBeenCalledWith({
      commitId: 'c1',
      body: { actualStatus: 'DONE' },
    });
  });

  it('debounces reflection PATCHes at 750ms', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime.bind(vi) });
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    await user.type(screen.getByRole('textbox', { name: /reflection/i }), 'Met goals.');

    // Before debounce window expires -> no fire.
    expect(mockUpdateReflectionTrigger).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(800);
    });

    await waitFor(() => expect(mockUpdateReflectionTrigger).toHaveBeenCalledTimes(1));
    expect(mockUpdateReflectionTrigger).toHaveBeenCalledWith({
      planId: PLAN_ID,
      body: { reflectionNote: 'Met goals.' },
    });
  });

  it('submits the LOCKED -> RECONCILED transition on Submit click', async () => {
    const user = userEvent.setup();
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    await user.click(screen.getByRole('button', { name: /submit reconciliation/i }));

    await waitFor(() => expect(mockTransitionTrigger).toHaveBeenCalledTimes(1));
    expect(mockTransitionTrigger).toHaveBeenCalledWith({
      planId: PLAN_ID,
      body: { to: 'RECONCILED' },
    });
  });

  it('shows the submitting copy and disables the button while the transition is pending', () => {
    mockUseTransitionMutation.mockReturnValue([
      mockTransitionTrigger,
      { isLoading: true, error: undefined },
    ]);
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    const submitBtn = screen.getByRole('button', { name: /submitting/i });
    expect(submitBtn).toBeDisabled();
  });

  it('surfaces a banner when the submit transition errors', () => {
    mockUseTransitionMutation.mockReturnValue([
      mockTransitionTrigger,
      { isLoading: false, error: { status: 500 } },
    ]);
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);
    expect(screen.getByTestId('reconcile-submit-error')).toBeInTheDocument();
  });

  it('carries a single eligible commit when its row Carry button is clicked', async () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit({ id: 'c-missed', actualStatus: 'MISSED' })],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    const user = userEvent.setup();
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    await user.click(screen.getByRole('button', { name: /carry to next week/i }));

    expect(mockCarryForwardTrigger).toHaveBeenCalledWith({ commitId: 'c-missed' });
  });

  it('carries every eligible commit on the bulk Carry-all button click', async () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [
        commit({ id: 'c-missed', actualStatus: 'MISSED' }),
        commit({ id: 'c-partial', actualStatus: 'PARTIAL' }),
        commit({ id: 'c-done', actualStatus: 'DONE' }),
      ],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    const user = userEvent.setup();
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    await user.click(screen.getByRole('button', { name: /carry all missed\/partial/i }));

    expect(mockCarryForwardTrigger).toHaveBeenCalledTimes(2);
    expect(mockCarryForwardTrigger).toHaveBeenCalledWith({ commitId: 'c-missed' });
    expect(mockCarryForwardTrigger).toHaveBeenCalledWith({ commitId: 'c-partial' });
  });
});
