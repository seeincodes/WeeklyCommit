import { act, render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
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

  describe('stuck-pulse transition animation', () => {
    beforeEach(() => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
    });
    afterEach(() => {
      vi.useRealTimers();
    });

    it('plays the pulse on the false→true (2→3) transition into stuck', () => {
      const { rerender } = render(<CarryStreakBadge carryStreak={2} />);
      let badge = screen.getByTestId('carry-streak-badge');
      // At streak 2, not stuck yet -- no animation class.
      expect(badge.className).not.toContain('animate-stuck-pulse');

      // Bump to 3: enters stuck. Effect runs, animation class is applied.
      rerender(<CarryStreakBadge carryStreak={3} />);
      badge = screen.getByTestId('carry-streak-badge');
      expect(badge).toHaveAttribute('data-stuck', 'true');
      expect(badge.className).toContain('animate-stuck-pulse');

      // After the animation window (700ms), the class is removed so the
      // badge sits at its baseline -- a future rerender with the same
      // streak does not re-fire.
      act(() => {
        vi.advanceTimersByTime(750);
      });
      badge = screen.getByTestId('carry-streak-badge');
      expect(badge.className).not.toContain('animate-stuck-pulse');
    });

    it('does not play the pulse when the badge mounts already stuck', () => {
      // First render at carryStreak=4 -- the badge is born stuck. We do
      // NOT want to fire the attention-grabber on mount; only on the
      // transition INTO stuck during a session.
      render(<CarryStreakBadge carryStreak={4} />);
      const badge = screen.getByTestId('carry-streak-badge');
      expect(badge).toHaveAttribute('data-stuck', 'true');
      expect(badge.className).not.toContain('animate-stuck-pulse');
    });

    it('does not play the pulse on a stuck→stuck rerender (streak grows but stays stuck)', () => {
      const { rerender } = render(<CarryStreakBadge carryStreak={3} />);
      // Even though carryStreak just changed, we mounted at 3 (already stuck),
      // so no animation. Now bump to 4 -- still stuck, still no animation.
      rerender(<CarryStreakBadge carryStreak={4} />);
      const badge = screen.getByTestId('carry-streak-badge');
      expect(badge.className).not.toContain('animate-stuck-pulse');
    });
  });
});
