import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ReconcileTable } from './ReconcileTable';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';

function commit(overrides: Partial<WeeklyCommitResponse> = {}): WeeklyCommitResponse {
  return {
    id: 'c1',
    planId: 'p1',
    title: 'Sample commit',
    supportingOutcomeId: 'so_1',
    chessTier: 'ROCK',
    displayOrder: 0,
    actualStatus: 'PENDING',
    ...overrides,
  };
}

describe('<ReconcileTable />', () => {
  it('renders one row per commit with the title', () => {
    const commits = [
      commit({ id: 'a', title: 'Land RCDO picker' }),
      commit({ id: 'b', title: 'Write the doc' }),
    ];
    render(<ReconcileTable commits={commits} onUpdate={vi.fn()} />);

    const rows = screen.getAllByRole('row');
    // 1 header row + 2 commit rows
    expect(rows).toHaveLength(3);
    expect(screen.getByText('Land RCDO picker')).toBeInTheDocument();
    expect(screen.getByText('Write the doc')).toBeInTheDocument();
  });

  it('exposes DONE / PARTIAL / MISSED radios per row', () => {
    render(<ReconcileTable commits={[commit({ id: 'a' })]} onUpdate={vi.fn()} />);
    const radioGroup = screen.getByRole('radiogroup', { name: /status/i });
    expect(within(radioGroup).getByRole('radio', { name: /done/i })).toBeInTheDocument();
    expect(within(radioGroup).getByRole('radio', { name: /partial/i })).toBeInTheDocument();
    expect(within(radioGroup).getByRole('radio', { name: /missed/i })).toBeInTheDocument();
  });

  it('marks the radio matching the row’s current actualStatus as checked', () => {
    render(
      <ReconcileTable
        commits={[commit({ id: 'a', actualStatus: 'PARTIAL' })]}
        onUpdate={vi.fn()}
      />,
    );
    expect(screen.getByRole('radio', { name: /partial/i })).toBeChecked();
    expect(screen.getByRole('radio', { name: /done/i })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: /missed/i })).not.toBeChecked();
  });

  it('calls onUpdate with the new status when a radio is selected', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    render(<ReconcileTable commits={[commit({ id: 'a' })]} onUpdate={onUpdate} />);

    await user.click(screen.getByRole('radio', { name: /done/i }));

    expect(onUpdate).toHaveBeenCalledOnce();
    expect(onUpdate).toHaveBeenCalledWith('a', { actualStatus: 'DONE' });
  });

  it('calls onUpdate with the new actualNote when the textarea changes', async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    render(<ReconcileTable commits={[commit({ id: 'a' })]} onUpdate={onUpdate} />);

    const textarea = screen.getByRole('textbox', { name: /actual note/i });
    await user.type(textarea, 'X'); // single char so we can assert the exact patch

    expect(onUpdate).toHaveBeenCalledOnce();
    expect(onUpdate).toHaveBeenCalledWith('a', { actualNote: 'X' });
  });

  it('navigates between rows with ArrowDown / ArrowUp on a status radio', async () => {
    const user = userEvent.setup();
    const commits = [commit({ id: 'r1', title: 'Row 1' }), commit({ id: 'r2', title: 'Row 2' })];
    render(<ReconcileTable commits={commits} onUpdate={vi.fn()} />);

    // Focus the DONE radio in row 1 by clicking it (clicking a radio focuses it).
    const radios = screen.getAllByRole('radio', { name: /done/i });
    await user.click(radios[0]!);
    expect(radios[0]).toHaveFocus();

    await user.keyboard('{ArrowDown}');
    expect(radios[1]).toHaveFocus();

    await user.keyboard('{ArrowUp}');
    expect(radios[0]).toHaveFocus();
  });

  it('shows an empty placeholder when there are no commits', () => {
    render(<ReconcileTable commits={[]} onUpdate={vi.fn()} />);
    expect(screen.getByTestId('reconcile-table-empty')).toBeInTheDocument();
  });
});
