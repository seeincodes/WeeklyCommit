import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import type { MemberCard as MemberCardType } from '@wc/rtk-api-client';
import { MemberCard } from './MemberCard';

function member(overrides: Partial<MemberCardType> = {}): MemberCardType {
  return {
    employeeId: 'emp-1',
    name: 'Ada Lovelace',
    planState: 'RECONCILED',
    topRock: { commitId: 'c-1', title: 'Land the picker spike' },
    tierCounts: { ROCK: 2, PEBBLE: 3, SAND: 1 },
    reflectionPreview: 'Unblocked the picker; WireMock contract drift took longer than expected.',
    flags: [],
    ...overrides,
  };
}

describe('<MemberCard />', () => {
  it('renders the member name', () => {
    render(<MemberCard member={member({ name: 'Ada Lovelace' })} onClick={vi.fn()} />);
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument();
  });

  it('renders the plan state badge', () => {
    render(<MemberCard member={member({ planState: 'RECONCILED' })} onClick={vi.fn()} />);
    expect(screen.getByTestId('member-card-state')).toHaveTextContent(/reconciled/i);
  });

  it('renders the Top Rock title when present', () => {
    render(
      <MemberCard
        member={member({ topRock: { commitId: 'c-1', title: 'Land the picker spike' } })}
        onClick={vi.fn()}
      />,
    );
    expect(screen.getByTestId('member-card-top-rock')).toHaveTextContent('Land the picker spike');
  });

  it('renders a "No Top Rock" indicator when topRock is absent', () => {
    // Omit topRock entirely (rather than set undefined) -- exactOptionalPropertyTypes rejects
    // explicit `undefined` for optional fields.
    const { topRock: _topRock, ...withoutTopRock } = member();
    render(<MemberCard member={withoutTopRock} onClick={vi.fn()} />);
    expect(screen.getByTestId('member-card-no-top-rock')).toBeInTheDocument();
  });

  it('renders the tier shape (Rock/Pebble/Sand counts)', () => {
    render(
      <MemberCard
        member={member({ tierCounts: { ROCK: 2, PEBBLE: 3, SAND: 1 } })}
        onClick={vi.fn()}
      />,
    );
    const tiers = screen.getByTestId('member-card-tiers');
    expect(tiers).toHaveTextContent('2'); // rocks
    expect(tiers).toHaveTextContent('3'); // pebbles
    expect(tiers).toHaveTextContent('1'); // sand
  });

  it('renders the reflection preview truncated to ~80 chars', () => {
    const longPreview = 'a'.repeat(200);
    render(<MemberCard member={member({ reflectionPreview: longPreview })} onClick={vi.fn()} />);
    const preview = screen.getByTestId('member-card-reflection');
    expect(preview.textContent?.length ?? 0).toBeLessThanOrEqual(83); // 80 + ellipsis
    expect(preview.textContent?.endsWith('…')).toBe(true);
  });

  it('renders the reflection preview verbatim when ≤ 80 chars', () => {
    const shortPreview = 'short note';
    render(<MemberCard member={member({ reflectionPreview: shortPreview })} onClick={vi.fn()} />);
    expect(screen.getByTestId('member-card-reflection')).toHaveTextContent('short note');
  });

  it('omits the reflection block when reflectionPreview is empty/undefined', () => {
    // Same exactOptionalPropertyTypes constraint -- omit rather than set undefined.
    const { reflectionPreview: _r, ...withoutPreview } = member();
    render(<MemberCard member={withoutPreview} onClick={vi.fn()} />);
    expect(screen.queryByTestId('member-card-reflection')).not.toBeInTheDocument();
  });

  it('renders one badge per flag', () => {
    render(
      <MemberCard
        member={member({ flags: ['UNREVIEWED_72H', 'STUCK_COMMIT'] })}
        onClick={vi.fn()}
      />,
    );
    expect(screen.getByTestId('flag-UNREVIEWED_72H')).toBeInTheDocument();
    expect(screen.getByTestId('flag-STUCK_COMMIT')).toBeInTheDocument();
  });

  it('does not render the flags strip when flags is empty', () => {
    render(<MemberCard member={member({ flags: [] })} onClick={vi.fn()} />);
    expect(screen.queryByTestId('member-card-flags')).not.toBeInTheDocument();
  });

  it('calls onClick with the employeeId when the card is activated', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<MemberCard member={member({ employeeId: 'emp-42' })} onClick={onClick} />);

    await user.click(screen.getByRole('button', { name: /ada lovelace/i }));

    expect(onClick).toHaveBeenCalledOnce();
    expect(onClick).toHaveBeenCalledWith('emp-42');
  });

  it('is keyboard-activatable via Enter and Space', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<MemberCard member={member({ employeeId: 'emp-7' })} onClick={onClick} />);

    const button = screen.getByRole('button', { name: /ada lovelace/i });
    button.focus();
    await user.keyboard('{Enter}');
    expect(onClick).toHaveBeenCalledWith('emp-7');
    await user.keyboard(' ');
    expect(onClick).toHaveBeenCalledTimes(2);
  });
});
