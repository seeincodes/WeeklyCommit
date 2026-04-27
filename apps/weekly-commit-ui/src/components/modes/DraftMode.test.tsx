import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import type { SupportingOutcome, WeeklyCommitResponse } from '@wc/rtk-api-client';
import { DraftMode } from './DraftMode';

// No shared MSW server in this codebase; mock the RTK Query hooks directly,
// matching the established WeekEditor.test.tsx pattern. Each hook's mock
// returns either a query result tuple-shaped object or, for mutations, the
// `[trigger, status]` tuple RTK Query exposes.
const mockUseListCommitsQuery = vi.hoisted(() => vi.fn());
const mockCreateCommitTrigger = vi.hoisted(() => vi.fn());
const mockUseCreateCommitMutation = vi.hoisted(() => vi.fn());
const mockTransitionTrigger = vi.hoisted(() => vi.fn());
const mockUseTransitionMutation = vi.hoisted(() => vi.fn());
const mockDeleteCommitTrigger = vi.hoisted(() => vi.fn());
const mockUseDeleteCommitMutation = vi.hoisted(() => vi.fn());
const mockUpdateCommitTrigger = vi.hoisted(() => vi.fn());
const mockUseUpdateCommitMutation = vi.hoisted(() => vi.fn());
const mockUseGetSupportingOutcomesQuery = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useListCommitsQuery: mockUseListCommitsQuery,
    useCreateCommitMutation: mockUseCreateCommitMutation,
    useTransitionMutation: mockUseTransitionMutation,
    useDeleteCommitMutation: mockUseDeleteCommitMutation,
    useUpdateCommitMutation: mockUseUpdateCommitMutation,
    useGetSupportingOutcomesQuery: mockUseGetSupportingOutcomesQuery,
  };
});

const PLAN_ID = '99999999-9999-9999-9999-999999999999';

const SAMPLE_OUTCOME: SupportingOutcome = {
  id: 'so-1',
  label: 'Hit revenue target',
  active: true,
  breadcrumb: {
    rallyCry: { id: 'rc', label: 'Win the quarter' },
    definingObjective: { id: 'do', label: 'Pipeline' },
    coreOutcome: { id: 'co', label: 'New ARR' },
    supportingOutcome: { id: 'so-1', label: 'Hit revenue target' },
  },
};

function commit(overrides: Partial<WeeklyCommitResponse> = {}): WeeklyCommitResponse {
  return {
    id: 'c1',
    planId: PLAN_ID,
    title: 'Existing commit',
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

describe('<DraftMode />', () => {
  beforeEach(() => {
    mockUseListCommitsQuery.mockReset();
    mockCreateCommitTrigger.mockReset();
    mockUseCreateCommitMutation.mockReset();
    mockTransitionTrigger.mockReset();
    mockUseTransitionMutation.mockReset();
    mockDeleteCommitTrigger.mockReset();
    mockUseDeleteCommitMutation.mockReset();
    mockUpdateCommitTrigger.mockReset();
    mockUseUpdateCommitMutation.mockReset();
    mockUseGetSupportingOutcomesQuery.mockReset();

    // Sensible defaults that the green path expects.
    mockUseListCommitsQuery.mockReturnValue({
      data: [],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    // unwrap() is called by DraftMode's submit handler; the mocked trigger
    // resolves to a trivial commit so the form clear path runs.
    mockCreateCommitTrigger.mockReturnValue({
      unwrap: () => Promise.resolve(commit({ id: 'created' })),
    });
    mockUseCreateCommitMutation.mockReturnValue([
      mockCreateCommitTrigger,
      { isLoading: false, error: undefined },
    ]);
    mockTransitionTrigger.mockReturnValue({
      unwrap: () => Promise.resolve({}),
    });
    mockUseTransitionMutation.mockReturnValue([
      mockTransitionTrigger,
      { isLoading: false, error: undefined },
    ]);
    mockDeleteCommitTrigger.mockReturnValue({ unwrap: () => Promise.resolve(undefined) });
    mockUseDeleteCommitMutation.mockReturnValue([
      mockDeleteCommitTrigger,
      { isLoading: false, error: undefined },
    ]);
    mockUpdateCommitTrigger.mockReturnValue({ unwrap: () => Promise.resolve(commit()) });
    mockUseUpdateCommitMutation.mockReturnValue([
      mockUpdateCommitTrigger,
      { isLoading: false, error: undefined },
    ]);
    mockUseGetSupportingOutcomesQuery.mockReturnValue({
      data: [SAMPLE_OUTCOME],
      error: undefined,
      isLoading: false,
    });
  });

  it('renders the loading sentinel while commits are in flight', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
      isFetching: true,
    });
    renderWithStore(<DraftMode planId={PLAN_ID} />);
    expect(screen.getByTestId('draft-loading')).toBeInTheDocument();
  });

  it('renders the error sentinel when the commits query errors', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: undefined,
      error: { status: 500 },
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<DraftMode planId={PLAN_ID} />);
    expect(screen.getByTestId('draft-error')).toBeInTheDocument();
  });

  it('renders the commit-create form, the StateBadge, the Lock Week button, and an empty list', () => {
    renderWithStore(<DraftMode planId={PLAN_ID} />);

    expect(screen.getByTestId('week-editor-draft')).toBeInTheDocument();
    expect(screen.getByTestId('commit-create-form')).toBeInTheDocument();
    expect(screen.getByTestId('state-badge')).toHaveAttribute('data-state', 'DRAFT');
    expect(screen.getByRole('button', { name: /lock week/i })).toBeInTheDocument();
    // No existing commits -> no draft rows.
    expect(screen.queryAllByTestId(/^draft-row-/)).toHaveLength(0);
  });

  it('lists existing commits using the configured row testid', () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit({ id: 'c-existing', title: 'Already here' })],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    renderWithStore(<DraftMode planId={PLAN_ID} />);
    expect(screen.getByTestId('draft-row-c-existing')).toBeInTheDocument();
    expect(screen.getByText(/already here/i)).toBeInTheDocument();
  });

  it('POSTs a new commit when the form is submitted', async () => {
    const user = userEvent.setup();
    renderWithStore(<DraftMode planId={PLAN_ID} />);

    await user.type(screen.getByLabelText(/title/i), 'Ship picker');
    await user.click(screen.getByRole('option', { name: /hit revenue target/i }));
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    await waitFor(() => expect(mockCreateCommitTrigger).toHaveBeenCalledTimes(1));
    expect(mockCreateCommitTrigger).toHaveBeenCalledWith({
      planId: PLAN_ID,
      body: {
        title: 'Ship picker',
        supportingOutcomeId: 'so-1',
        chessTier: 'PEBBLE',
      },
    });
  });

  it('transitions DRAFT → LOCKED on Lock Week click', async () => {
    const user = userEvent.setup();
    renderWithStore(<DraftMode planId={PLAN_ID} />);

    await user.click(screen.getByRole('button', { name: /lock week/i }));

    await waitFor(() => expect(mockTransitionTrigger).toHaveBeenCalledTimes(1));
    expect(mockTransitionTrigger).toHaveBeenCalledWith({
      planId: PLAN_ID,
      body: { to: 'LOCKED' },
    });
  });

  it('shows the locking copy and disables the button while the transition is pending', () => {
    mockUseTransitionMutation.mockReturnValue([
      mockTransitionTrigger,
      { isLoading: true, error: undefined },
    ]);
    renderWithStore(<DraftMode planId={PLAN_ID} />);

    const lockBtn = screen.getByRole('button', { name: /locking/i });
    expect(lockBtn).toBeDisabled();
  });

  it('surfaces a banner when create or lock errors', () => {
    mockUseCreateCommitMutation.mockReturnValue([
      mockCreateCommitTrigger,
      { isLoading: false, error: { status: 500 } },
    ]);
    renderWithStore(<DraftMode planId={PLAN_ID} />);
    expect(screen.getByTestId('draft-mutation-error')).toBeInTheDocument();
  });

  it('deletes a commit when the row Delete button is clicked', async () => {
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit({ id: 'c-existing' })],
      error: undefined,
      isLoading: false,
      isFetching: false,
    });
    const user = userEvent.setup();
    renderWithStore(<DraftMode planId={PLAN_ID} />);

    await user.click(screen.getByRole('button', { name: /delete/i }));
    expect(mockDeleteCommitTrigger).toHaveBeenCalledWith({ commitId: 'c-existing' });
  });
});
