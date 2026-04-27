import { render, screen, within } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import type { MemberCard, RollupResponse } from '@wc/rtk-api-client';
import { TeamRollup } from './TeamRollup';

function member(overrides: Partial<MemberCard> = {}): MemberCard {
  return {
    employeeId: 'e1',
    name: 'Ada',
    planState: 'DRAFT',
    tierCounts: { ROCK: 0, PEBBLE: 0, SAND: 0 },
    reflectionPreview: '',
    flags: [],
    ...overrides,
  };
}

function rollup(overrides: Partial<RollupResponse> = {}): RollupResponse {
  return {
    alignmentPct: 0.91,
    completionPct: 0.76,
    tierDistribution: { ROCK: 14, PEBBLE: 39, SAND: 22 },
    unreviewedCount: 3,
    stuckCommitCount: 2,
    members: [],
    ...overrides,
  };
}

describe('<TeamRollup />', () => {
  it('renders the aggregate stats strip with alignment + completion percentages', () => {
    render(
      <TeamRollup
        rollup={rollup({ alignmentPct: 0.91, completionPct: 0.76 })}
        renderMember={(m) => <span key={m.employeeId}>{m.name}</span>}
      />,
    );
    const stats = screen.getByTestId('team-rollup-stats');
    expect(stats).toHaveTextContent('91%'); // alignment
    expect(stats).toHaveTextContent('76%'); // completion
  });

  it('renders the unreviewed + stuck-commit counts in the stats strip', () => {
    render(
      <TeamRollup
        rollup={rollup({ unreviewedCount: 3, stuckCommitCount: 2 })}
        renderMember={(m) => <span key={m.employeeId}>{m.name}</span>}
      />,
    );
    const stats = screen.getByTestId('team-rollup-stats');
    expect(stats).toHaveTextContent('3'); // unreviewed
    expect(stats).toHaveTextContent('2'); // stuck
  });

  it('renders the tier distribution counts', () => {
    render(
      <TeamRollup
        rollup={rollup({ tierDistribution: { ROCK: 14, PEBBLE: 39, SAND: 22 } })}
        renderMember={(m) => <span key={m.employeeId}>{m.name}</span>}
      />,
    );
    const tiers = screen.getByTestId('team-rollup-tiers');
    expect(tiers).toHaveTextContent('14');
    expect(tiers).toHaveTextContent('39');
    expect(tiers).toHaveTextContent('22');
  });

  it('orders flagged members before unflagged members (flagged-first ordering per [MVP9])', () => {
    const members: MemberCard[] = [
      member({ employeeId: 'e1', name: 'Ada', flags: [] }),
      member({ employeeId: 'e2', name: 'Ben', flags: ['UNREVIEWED_72H'] }),
      member({ employeeId: 'e3', name: 'Cleo', flags: [] }),
      member({ employeeId: 'e4', name: 'Dax', flags: ['STUCK_COMMIT', 'NO_TOP_ROCK'] }),
    ];
    render(
      <TeamRollup
        rollup={rollup({ members })}
        renderMember={(m) => (
          <li key={m.employeeId} data-testid="member-row">
            {m.name}
          </li>
        )}
      />,
    );

    const rows = screen.getAllByTestId('member-row');
    // Ben + Dax flagged (top, alphabetical), then Ada + Cleo unflagged (alphabetical).
    expect(rows.map((r) => r.textContent)).toEqual(['Ben', 'Dax', 'Ada', 'Cleo']);
  });

  it('orders members alphabetically by name within the same flagged-bucket', () => {
    const members: MemberCard[] = [
      member({ employeeId: 'e2', name: 'Zach', flags: ['STUCK_COMMIT'] }),
      member({ employeeId: 'e1', name: 'Ada', flags: ['UNREVIEWED_72H'] }),
    ];
    render(
      <TeamRollup
        rollup={rollup({ members })}
        renderMember={(m) => (
          <li key={m.employeeId} data-testid="member-row">
            {m.name}
          </li>
        )}
      />,
    );

    const rows = screen.getAllByTestId('member-row');
    expect(rows.map((r) => r.textContent)).toEqual(['Ada', 'Zach']);
  });

  it('renders an empty-state placeholder when there are no members', () => {
    render(
      <TeamRollup
        rollup={rollup({ members: [] })}
        renderMember={(m) => <span key={m.employeeId}>{m.name}</span>}
      />,
    );
    expect(screen.getByTestId('team-rollup-empty')).toBeInTheDocument();
  });

  it('still renders the stats strip when members is empty (manager with no reports yet)', () => {
    render(
      <TeamRollup
        rollup={rollup({ members: [] })}
        renderMember={(m) => <span key={m.employeeId}>{m.name}</span>}
      />,
    );
    // Aggregates may be 0% but the strip exists so the layout is stable.
    expect(screen.getByTestId('team-rollup-stats')).toBeInTheDocument();
  });

  it('passes each member to renderMember exactly once', () => {
    const members: MemberCard[] = [
      member({ employeeId: 'e1', name: 'Ada' }),
      member({ employeeId: 'e2', name: 'Ben' }),
    ];
    render(
      <TeamRollup
        rollup={rollup({ members })}
        renderMember={(m) => (
          <li key={m.employeeId} data-testid={`member-${m.employeeId}`}>
            {m.name}
          </li>
        )}
      />,
    );
    expect(within(screen.getByTestId('member-e1')).getByText('Ada')).toBeInTheDocument();
    expect(within(screen.getByTestId('member-e2')).getByText('Ben')).toBeInTheDocument();
  });
});
