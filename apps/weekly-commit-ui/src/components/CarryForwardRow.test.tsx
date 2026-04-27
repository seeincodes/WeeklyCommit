import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { CarryForwardRow, CarryAllButton } from './CarryForwardRow';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';

function commit(overrides: Partial<WeeklyCommitResponse> = {}): WeeklyCommitResponse {
  return {
    id: 'c1',
    planId: 'p1',
    title: 'Sample',
    supportingOutcomeId: 'so_1',
    chessTier: 'ROCK',
    displayOrder: 0,
    actualStatus: 'MISSED',
    ...overrides,
  };
}

describe('<CarryForwardRow />', () => {
  it('renders a "carry to next week" button for a MISSED commit', () => {
    const c = commit({ id: 'c1', title: 'Land RCDO picker', actualStatus: 'MISSED' });
    render(<CarryForwardRow commit={c} onCarry={vi.fn()} />);
    expect(screen.getByRole('button', { name: /carry to next week/i })).toBeInTheDocument();
  });

  it('renders a "carry to next week" button for a PARTIAL commit', () => {
    const c = commit({ id: 'c2', title: 'Doc draft', actualStatus: 'PARTIAL' });
    render(<CarryForwardRow commit={c} onCarry={vi.fn()} />);
    expect(screen.getByRole('button', { name: /carry to next week/i })).toBeInTheDocument();
  });

  it('does NOT render the carry button for a DONE commit', () => {
    const c = commit({ id: 'c3', actualStatus: 'DONE' });
    render(<CarryForwardRow commit={c} onCarry={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /carry to next week/i })).not.toBeInTheDocument();
  });

  it('does NOT render the carry button for a PENDING commit', () => {
    const c = commit({ id: 'c4', actualStatus: 'PENDING' });
    render(<CarryForwardRow commit={c} onCarry={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /carry to next week/i })).not.toBeInTheDocument();
  });

  it('calls onCarry with the commit id when the carry button is clicked', async () => {
    const user = userEvent.setup();
    const onCarry = vi.fn();
    const c = commit({ id: 'c5', actualStatus: 'MISSED' });
    render(<CarryForwardRow commit={c} onCarry={onCarry} />);

    await user.click(screen.getByRole('button', { name: /carry to next week/i }));

    expect(onCarry).toHaveBeenCalledOnce();
    expect(onCarry).toHaveBeenCalledWith('c5');
  });

  it('shows an "already carried" indicator when carriedForwardToId is set', () => {
    const c = commit({
      id: 'c6',
      actualStatus: 'MISSED',
      carriedForwardToId: 'twin-id',
    });
    render(<CarryForwardRow commit={c} onCarry={vi.fn()} />);
    expect(screen.getByTestId('carry-forward-already-done')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /carry to next week/i })).not.toBeInTheDocument();
  });
});

describe('<CarryAllButton />', () => {
  it('renders a single "carry all missed/partial" button', () => {
    const commits = [commit({ actualStatus: 'MISSED' }), commit({ actualStatus: 'PARTIAL' })];
    render(<CarryAllButton commits={commits} onCarryAll={vi.fn()} />);
    expect(screen.getByRole('button', { name: /carry all/i })).toBeInTheDocument();
  });

  it('disables itself when there are no carry-eligible commits', () => {
    const commits = [commit({ actualStatus: 'DONE' })];
    render(<CarryAllButton commits={commits} onCarryAll={vi.fn()} />);
    expect(screen.getByRole('button', { name: /carry all/i })).toBeDisabled();
  });

  it('calls onCarryAll with only the MISSED + PARTIAL commit ids', async () => {
    const user = userEvent.setup();
    const onCarryAll = vi.fn();
    const commits = [
      commit({ id: 'a', actualStatus: 'DONE' }),
      commit({ id: 'b', actualStatus: 'MISSED' }),
      commit({ id: 'c', actualStatus: 'PARTIAL' }),
      commit({ id: 'd', actualStatus: 'PENDING' }),
      commit({ id: 'e', actualStatus: 'MISSED', carriedForwardToId: 'twin' }),
    ];
    render(<CarryAllButton commits={commits} onCarryAll={onCarryAll} />);

    await user.click(screen.getByRole('button', { name: /carry all/i }));

    expect(onCarryAll).toHaveBeenCalledOnce();
    // Only b and c qualify: MISSED/PARTIAL AND not already carried.
    expect(onCarryAll).toHaveBeenCalledWith(['b', 'c']);
  });
});
