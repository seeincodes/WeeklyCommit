import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import type { SupportingOutcome } from '@wc/rtk-api-client';
import { CommitCreateForm } from './CommitCreateForm';

// CommitCreateForm renders <RCDOPickerContainer /> internally, which calls
// useGetSupportingOutcomesQuery. We mock the hook to keep the picker in a
// deterministic loaded state, mirroring the established WeekEditor /
// RCDOPickerContainer test pattern (no shared MSW server in this codebase).
const mockUseGetSupportingOutcomesQuery = vi.hoisted(() => vi.fn());

vi.mock('@wc/rtk-api-client', async () => {
  const actual = await vi.importActual<typeof import('@wc/rtk-api-client')>('@wc/rtk-api-client');
  return {
    ...actual,
    useGetSupportingOutcomesQuery: mockUseGetSupportingOutcomesQuery,
  };
});

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

describe('<CommitCreateForm />', () => {
  beforeEach(() => {
    mockUseGetSupportingOutcomesQuery.mockReset();
    mockUseGetSupportingOutcomesQuery.mockReturnValue({
      data: [SAMPLE_OUTCOME],
      error: undefined,
      isLoading: false,
    });
  });

  it('disables submit until both title and supporting outcome are set', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderWithStore(<CommitCreateForm onSubmit={onSubmit} />);

    expect(screen.getByRole('button', { name: /add commit/i })).toBeDisabled();

    // Title only -> still disabled (outcome not yet selected).
    await user.type(screen.getByLabelText(/title/i), 'Ship the picker integration');
    expect(screen.getByRole('button', { name: /add commit/i })).toBeDisabled();

    // Now select the outcome via the listbox option that the picker exposes.
    await user.click(screen.getByRole('option', { name: /hit revenue target/i }));
    expect(screen.getByRole('button', { name: /add commit/i })).toBeEnabled();
  });

  it('emits the commit payload on submit', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderWithStore(<CommitCreateForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/title/i), 'Ship the picker integration');
    await user.click(screen.getByRole('option', { name: /hit revenue target/i }));
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Ship the picker integration',
      supportingOutcomeId: 'so-1',
      chessTier: 'PEBBLE',
    });
  });

  it('includes estimatedHours when the user fills it in', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderWithStore(<CommitCreateForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/title/i), 'Hours commit');
    await user.click(screen.getByRole('option', { name: /hit revenue target/i }));
    await user.type(screen.getByLabelText(/estimated hours/i), '4.5');
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Hours commit',
      supportingOutcomeId: 'so-1',
      chessTier: 'PEBBLE',
      estimatedHours: 4.5,
    });
  });

  it('respects the chosen tier', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderWithStore(<CommitCreateForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/title/i), 'Rock-tier commit');
    await user.click(screen.getByRole('option', { name: /hit revenue target/i }));
    await user.selectOptions(screen.getByLabelText(/tier/i), 'ROCK');
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Rock-tier commit',
      supportingOutcomeId: 'so-1',
      chessTier: 'ROCK',
    });
  });

  it('clears the form after a successful submit', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithStore(<CommitCreateForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/title/i), 'Ship the picker integration');
    await user.click(screen.getByRole('option', { name: /hit revenue target/i }));
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    expect(screen.getByLabelText(/title/i)).toHaveValue('');
    // Submit goes back to disabled because the outcome selection is also cleared.
    expect(screen.getByRole('button', { name: /add commit/i })).toBeDisabled();
  });

  it('honors the disabled prop (no submit fires)', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderWithStore(<CommitCreateForm onSubmit={onSubmit} disabled />);

    await user.type(screen.getByLabelText(/title/i), 'Whatever');
    await user.click(screen.getByRole('option', { name: /hit revenue target/i }));
    expect(screen.getByRole('button', { name: /add commit/i })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /add commit/i }));
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
