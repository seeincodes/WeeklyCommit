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
 * Both render-paths share the same `data-testid` + `data-stuck` attributes
 * so consumers (manager rollup, IC drawer, edit row) can style consistently
 * and a single Phase-2 polish task can layer the ≥2→≥3 transition animation.
 */
export function CarryStreakBadge({ carryStreak }: CarryStreakBadgeProps) {
  if (carryStreak < 2) return null;
  const stuck = carryStreak >= 3;
  return (
    <span
      data-testid="carry-streak-badge"
      data-stuck={stuck ? 'true' : 'false'}
      aria-label={`Carried forward ${carryStreak} weeks in a row`}
      className={
        stuck
          ? 'inline-flex items-center rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700'
          : 'inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700'
      }
    >
      {carryStreak}x
    </span>
  );
}
