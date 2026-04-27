import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import type { WeeklyPlanResponse, WeeklyCommitResponse } from '@wc/rtk-api-client';
import { draftFormSlice } from '../store/draftFormSlice';
import { IcDrawer } from './IcDrawer';

const mockUseGetPlanByEmployeeAndWeekQuery = vi.hoisted(() => vi.fn());
const mockUseListCommitsQuery = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useGetPlanByEmployeeAndWeekQuery: mockUseGetPlanByEmployeeAndWeekQuery,
    useListCommitsQuery: mockUseListCommitsQuery,
  };
});

function renderDrawer(props: Partial<Parameters<typeof IcDrawer>[0]> = {}) {
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
      <IcDrawer
        employeeId="emp-1"
        employeeName="Ada Lovelace"
        weekStart="2026-04-27"
        onClose={vi.fn()}
        {...props}
      />
    </Provider>,
  );
}

const SAMPLE_PLAN: WeeklyPlanResponse = {
  id: 'plan-1',
  employeeId: 'emp-1',
  weekStart: '2026-04-27',
  state: 'RECONCILED',
  version: 3,
  reflectionNote: 'A long reflection note that should appear in full inside the drawer.',
  reconciledAt: '2026-05-02T18:00:00Z',
};

function commit(overrides: Partial<WeeklyCommitResponse> = {}): WeeklyCommitResponse {
  return {
    id: 'c-1',
    planId: 'plan-1',
    title: 'Sample commit',
    supportingOutcomeId: 'so-1',
    chessTier: 'ROCK',
    displayOrder: 0,
    actualStatus: 'DONE',
    ...overrides,
  };
}

describe('<IcDrawer />', () => {
  beforeEach(() => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReset();
    mockUseListCommitsQuery.mockReset();
  });

  it('renders as a role=dialog with the employee name in the accessible label', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({ data: [], isLoading: false });
    renderDrawer({ employeeName: 'Ada Lovelace' });
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName(/ada lovelace/i);
  });

  it('shows a loading state while the plan query is in flight', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: undefined, isLoading: true });
    mockUseListCommitsQuery.mockReturnValue({ data: undefined, isLoading: false });
    renderDrawer();
    expect(screen.getByTestId('ic-drawer-loading')).toBeInTheDocument();
  });

  it('shows an empty-state message when no plan exists for that employee + week', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({
      data: undefined,
      error: { status: 404 },
      isLoading: false,
    });
    mockUseListCommitsQuery.mockReturnValue({ data: undefined, isLoading: false, skip: true });
    renderDrawer();
    expect(screen.getByTestId('ic-drawer-no-plan')).toBeInTheDocument();
  });

  it('renders the plan state badge', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({ data: [], isLoading: false });
    renderDrawer();
    expect(screen.getByTestId('state-badge')).toHaveTextContent(/reconciled/i);
  });

  it('renders the full reflection note (no truncation)', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({ data: [], isLoading: false });
    renderDrawer();
    expect(screen.getByTestId('ic-drawer-reflection')).toHaveTextContent(
      'A long reflection note that should appear in full inside the drawer.',
    );
  });

  it('shows a "no reflection yet" placeholder when reflectionNote is empty', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({
      data: { ...SAMPLE_PLAN, reflectionNote: '' },
      isLoading: false,
    });
    mockUseListCommitsQuery.mockReturnValue({ data: [], isLoading: false });
    renderDrawer();
    expect(screen.getByTestId('ic-drawer-no-reflection')).toBeInTheDocument();
  });

  it('renders the commit list with one row per commit', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({
      data: [
        commit({ id: 'c-1', title: 'Land RCDO picker' }),
        commit({ id: 'c-2', title: 'Write the doc', chessTier: 'PEBBLE' }),
      ],
      isLoading: false,
    });
    renderDrawer();
    expect(screen.getByText('Land RCDO picker')).toBeInTheDocument();
    expect(screen.getByText('Write the doc')).toBeInTheDocument();
  });

  it('surfaces the carry-streak badge for each commit with carryStreak >= 2', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({
      data: [
        // Two commits: one with streak 3 (stuck), one with streak 1 (no badge).
        commit({ id: 'c-1', derived: { carryStreak: 3, stuckFlag: true } }),
        commit({ id: 'c-2', derived: { carryStreak: 1, stuckFlag: false } }),
      ],
      isLoading: false,
    });
    renderDrawer();
    const badges = screen.getAllByTestId('carry-streak-badge');
    expect(badges).toHaveLength(1);
    expect(badges[0]).toHaveAttribute('data-stuck', 'true');
  });

  it('calls onClose when the close button is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({ data: [], isLoading: false });
    renderDrawer({ onClose });
    await user.click(screen.getByRole('button', { name: /close/i }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls onClose when Escape is pressed', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({ data: [], isLoading: false });
    renderDrawer({ onClose });
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls onClose when the backdrop is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({ data: [], isLoading: false });
    renderDrawer({ onClose });
    await user.click(screen.getByTestId('ic-drawer-backdrop'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('does NOT close when clicks happen inside the dialog (event bubbling guard)', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({
      data: [commit({ title: 'Inside-click target' })],
      isLoading: false,
    });
    renderDrawer({ onClose });
    await user.click(screen.getByText('Inside-click target'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('groups commits visually into chess tiers (delegates to <ChessTier />)', () => {
    mockUseGetPlanByEmployeeAndWeekQuery.mockReturnValue({ data: SAMPLE_PLAN, isLoading: false });
    mockUseListCommitsQuery.mockReturnValue({
      data: [
        commit({ id: 'r1', title: 'A rock', chessTier: 'ROCK' }),
        commit({ id: 'p1', title: 'A pebble', chessTier: 'PEBBLE' }),
        commit({ id: 's1', title: 'Some sand', chessTier: 'SAND' }),
      ],
      isLoading: false,
    });
    renderDrawer();
    // Three role=group sections from <ChessTier />.
    const groups = screen.getAllByRole('group');
    expect(groups).toHaveLength(3);
    expect(within(groups[0]!).getByText('A rock')).toBeInTheDocument();
    expect(within(groups[1]!).getByText('A pebble')).toBeInTheDocument();
    expect(within(groups[2]!).getByText('Some sand')).toBeInTheDocument();
  });
});
