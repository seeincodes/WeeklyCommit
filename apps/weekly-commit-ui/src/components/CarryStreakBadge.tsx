import { useEffect, useRef, useState } from 'react';

interface CarryStreakBadgeProps {
  /**
   * Server-derived carryStreak (walk of `carriedForwardFromId`, cap 52 per
   * [MVP7]). Lives on `WeeklyCommitResponse.derived.carryStreak`.
   */
  carryStreak: number;
}

/**
 * Visual streak indicator. Renders nothing for 0-1 (not yet a streak); a
 * neutral badge for streak == 2; flips to a "stuck" red style at streak >= 3
 * which is the manager-facing stuck-commit flag per [MVP7] + [MVP9].
 *
 * Plays a one-shot scale pulse on the transition INTO stuck (last value < 3
 * AND new value >= 3). The animation tells the user "this just became a
 * problem" rather than nagging on every render. Reduced-motion users skip
 * the animation via Tailwind's `motion-safe:` variant.
 *
 * Both render-paths share the same `data-testid` + `data-stuck` attributes
 * so consumers (manager rollup, IC drawer, edit row) can style consistently.
 */
export function CarryStreakBadge({ carryStreak }: CarryStreakBadgeProps) {
  const stuck = carryStreak >= 3;
  const [animateOnce, setAnimateOnce] = useState(false);
  const prevStuckRef = useRef(stuck);

  useEffect(() => {
    // Fire once on the false -> true transition. A subsequent render with
    // the same stuck state is a no-op; if the value drops below 3 (rare
    // in practice -- streaks only grow) and climbs back up, we'll fire again.
    if (stuck && !prevStuckRef.current) {
      setAnimateOnce(true);
      const handle = setTimeout(() => setAnimateOnce(false), 700);
      prevStuckRef.current = stuck;
      return () => clearTimeout(handle);
    }
    prevStuckRef.current = stuck;
    return undefined;
  }, [stuck]);

  if (carryStreak < 2) return null;

  const baseClasses =
    'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold transition-colors';
  const colorClasses = stuck ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-700';
  const animationClasses = animateOnce ? 'motion-safe:animate-stuck-pulse' : '';

  return (
    <span
      data-testid="carry-streak-badge"
      data-stuck={stuck ? 'true' : 'false'}
      aria-label={`Carried forward ${carryStreak} weeks in a row`}
      className={`${baseClasses} ${colorClasses} ${animationClasses}`.trim()}
    >
      {carryStreak}x
    </span>
  );
}
