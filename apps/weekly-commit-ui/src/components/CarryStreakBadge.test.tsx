import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { CarryStreakBadge } from './CarryStreakBadge';

describe('<CarryStreakBadge />', () => {
  it('renders nothing for streak 0 (no carries)', () => {
    const { container } = render(<CarryStreakBadge carryStreak={0} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing for streak 1 (single occurrence, not yet a streak)', () => {
    const { container } = render(<CarryStreakBadge carryStreak={1} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the streak badge at streak 2', () => {
    render(<CarryStreakBadge carryStreak={2} />);
    const badge = screen.getByTestId('carry-streak-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('2x');
    expect(badge).toHaveAttribute('data-stuck', 'false');
  });

  it('renders the stuck-flag style at streak 3', () => {
    render(<CarryStreakBadge carryStreak={3} />);
    const badge = screen.getByTestId('carry-streak-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute('data-stuck', 'true');
    expect(badge).toHaveTextContent('3x');
  });

  it('renders the stuck-flag style at streak 5 (still stuck)', () => {
    render(<CarryStreakBadge carryStreak={5} />);
    const badge = screen.getByTestId('carry-streak-badge');
    expect(badge).toHaveAttribute('data-stuck', 'true');
    expect(badge).toHaveTextContent('5x');
  });

  it('exposes a meaningful aria-label for the streak count', () => {
    render(<CarryStreakBadge carryStreak={4} />);
    expect(screen.getByLabelText(/carried forward 4 weeks/i)).toBeInTheDocument();
  });
});
