import { render, screen, within } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ChessTier } from './ChessTier';
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

describe('<ChessTier />', () => {
  it('renders three tier sections in Rock > Pebble > Sand order', () => {
    render(<ChessTier commits={[]} renderCommit={(c) => <span key={c.id}>{c.title}</span>} />);
    const sections = screen.getAllByRole('group');
    expect(sections).toHaveLength(3);
    expect(within(sections[0]!).getByRole('heading')).toHaveTextContent(/rock/i);
    expect(within(sections[1]!).getByRole('heading')).toHaveTextContent(/pebble/i);
    expect(within(sections[2]!).getByRole('heading')).toHaveTextContent(/sand/i);
  });

  it('groups commits into the correct tier section', () => {
    const commits = [
      commit({ id: 'r1', title: 'Big rock', chessTier: 'ROCK', displayOrder: 0 }),
      commit({ id: 'p1', title: 'A pebble', chessTier: 'PEBBLE', displayOrder: 0 }),
      commit({ id: 's1', title: 'Some sand', chessTier: 'SAND', displayOrder: 0 }),
      commit({ id: 'r2', title: 'Another rock', chessTier: 'ROCK', displayOrder: 1 }),
    ];

    render(<ChessTier commits={commits} renderCommit={(c) => <span key={c.id}>{c.title}</span>} />);

    const [rockSection, pebbleSection, sandSection] = screen.getAllByRole('group');
    expect(within(rockSection!).getByText('Big rock')).toBeInTheDocument();
    expect(within(rockSection!).getByText('Another rock')).toBeInTheDocument();
    expect(within(pebbleSection!).getByText('A pebble')).toBeInTheDocument();
    expect(within(sandSection!).getByText('Some sand')).toBeInTheDocument();
  });

  it('orders commits inside a tier by displayOrder ascending', () => {
    const commits = [
      commit({ id: 'r3', title: 'Third rock', chessTier: 'ROCK', displayOrder: 2 }),
      commit({ id: 'r1', title: 'First rock', chessTier: 'ROCK', displayOrder: 0 }),
      commit({ id: 'r2', title: 'Second rock', chessTier: 'ROCK', displayOrder: 1 }),
    ];

    render(<ChessTier commits={commits} renderCommit={(c) => <span key={c.id}>{c.title}</span>} />);

    const rockSection = screen.getAllByRole('group')[0]!;
    // Use the <ul> rows to skip the "rock" heading; commits render inside <li>.
    const items = within(rockSection)
      .getAllByRole('listitem')
      .map((el) => el.textContent);
    expect(items).toEqual(['First rock', 'Second rock', 'Third rock']);
  });

  it('marks the lowest-displayOrder ROCK as the Top Rock', () => {
    const commits = [
      commit({ id: 'r2', title: 'Second rock', chessTier: 'ROCK', displayOrder: 1 }),
      commit({ id: 'r1', title: 'First rock', chessTier: 'ROCK', displayOrder: 0 }),
    ];

    render(
      <ChessTier
        commits={commits}
        renderCommit={(c, isTopRock) => (
          <span key={c.id} data-testid={isTopRock ? 'top-rock' : 'not-top-rock'}>
            {c.title}
          </span>
        )}
      />,
    );

    const topRock = screen.getByTestId('top-rock');
    expect(topRock).toHaveTextContent('First rock');
    expect(screen.getAllByTestId('not-top-rock')).toHaveLength(1);
  });

  it('passes isTopRock=false to non-ROCK tiers (Top Rock is ROCK-only)', () => {
    const commits = [
      commit({ id: 'p1', title: 'Pebble alone', chessTier: 'PEBBLE', displayOrder: 0 }),
    ];

    render(
      <ChessTier
        commits={commits}
        renderCommit={(c, isTopRock) => (
          <span key={c.id} data-testid={isTopRock ? 'top-rock' : 'not-top-rock'}>
            {c.title}
          </span>
        )}
      />,
    );

    expect(screen.queryByTestId('top-rock')).not.toBeInTheDocument();
    expect(screen.getByTestId('not-top-rock')).toHaveTextContent('Pebble alone');
  });

  it('shows a per-tier empty placeholder when a tier has no commits', () => {
    render(<ChessTier commits={[]} renderCommit={(c) => <span key={c.id}>{c.title}</span>} />);
    expect(screen.getByTestId('chess-tier-empty-ROCK')).toBeInTheDocument();
    expect(screen.getByTestId('chess-tier-empty-PEBBLE')).toBeInTheDocument();
    expect(screen.getByTestId('chess-tier-empty-SAND')).toBeInTheDocument();
  });

  it('flags "no Top Rock" when the ROCK tier is empty', () => {
    const commits = [commit({ id: 'p1', title: 'Pebble', chessTier: 'PEBBLE', displayOrder: 0 })];
    render(<ChessTier commits={commits} renderCommit={(c) => <span key={c.id}>{c.title}</span>} />);
    expect(screen.getByTestId('chess-tier-no-top-rock')).toBeInTheDocument();
  });

  it('does NOT flag "no Top Rock" when at least one ROCK exists', () => {
    const commits = [commit({ id: 'r1', title: 'Rock', chessTier: 'ROCK' })];
    render(<ChessTier commits={commits} renderCommit={(c) => <span key={c.id}>{c.title}</span>} />);
    expect(screen.queryByTestId('chess-tier-no-top-rock')).not.toBeInTheDocument();
  });
});
