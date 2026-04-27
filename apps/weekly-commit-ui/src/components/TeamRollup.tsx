import { useMemo } from 'react';
import type { MemberCard, RollupResponse } from '@wc/rtk-api-client';

interface TeamRollupProps {
  rollup: RollupResponse;
  /**
   * Renders a single member row. The rollup is presentational from the
   * member-row standpoint -- it doesn't know what a card looks like, only
   * how to order members and frame them with the aggregate stats. The
   * parent passes a renderer; subtask 3's <MemberCard /> plugs in here.
   */
  renderMember: (member: MemberCard) => React.ReactNode;
}

/**
 * Manager team rollup container per [MVP9]. Two structural pieces:
 *
 *   - Aggregate stats strip (alignment %, completion %, tier distribution,
 *     unreviewed count, stuck-commit count). Always visible -- if a manager
 *     has no direct reports the strip still renders so the layout stays
 *     stable, with zeroes throughout.
 *   - Ordered member list. Sort key is "flagged-first then alphabetical":
 *     members with at least one flag float to the top so the manager's
 *     attention lands on the rows that need it. Within each bucket the
 *     order is alphabetical by name.
 */
export function TeamRollup({ rollup, renderMember }: TeamRollupProps) {
  const ordered = useMemo(() => orderMembers(rollup.members ?? []), [rollup.members]);

  return (
    <div className="flex flex-col gap-4" data-testid="team-rollup">
      <StatsStrip rollup={rollup} />
      <TierStrip tierDistribution={rollup.tierDistribution ?? {}} />
      {ordered.length === 0 ? (
        <div
          data-testid="team-rollup-empty"
          className="rounded border border-dashed border-gray-300 bg-gray-50 px-4 py-6 text-center text-sm text-gray-600"
        >
          <p className="font-medium text-gray-700">No direct reports for this week.</p>
          <p className="mt-1 text-gray-500">
            Members appear here once they’ve started their weekly plan.
          </p>
        </div>
      ) : (
        <ul className="flex flex-col gap-2">{ordered.map((m) => renderMember(m))}</ul>
      )}
    </div>
  );
}

function orderMembers(members: MemberCard[]): MemberCard[] {
  // `toSorted` would be cleaner but isn't universally typed in our TS lib target.
  // Plain copy + sort keeps the input array immutable.
  return [...members].sort((a, b) => {
    const aFlagged = (a.flags?.length ?? 0) > 0;
    const bFlagged = (b.flags?.length ?? 0) > 0;
    if (aFlagged !== bFlagged) return aFlagged ? -1 : 1;
    return (a.name ?? '').localeCompare(b.name ?? '');
  });
}

function StatsStrip({ rollup }: { rollup: RollupResponse }) {
  return (
    <div
      data-testid="team-rollup-stats"
      className="grid grid-cols-2 gap-3 sm:grid-cols-4 rounded border border-gray-200 bg-white p-3"
    >
      <Stat label="Alignment" value={percent(rollup.alignmentPct)} />
      <Stat label="Completion" value={percent(rollup.completionPct)} />
      <Stat label="Unreviewed" value={String(rollup.unreviewedCount ?? 0)} />
      <Stat label="Stuck commits" value={String(rollup.stuckCommitCount ?? 0)} />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col">
      <span className="text-xs text-gray-500">{label}</span>
      <span className="text-lg font-semibold text-gray-900">{value}</span>
    </div>
  );
}

function TierStrip({ tierDistribution }: { tierDistribution: Record<string, number> }) {
  // Render in canonical chess order so the tier comparison is consistent across
  // Members and the rollup; mirrors <ChessTier />'s ROCK > PEBBLE > SAND ordering.
  const tiers: ['ROCK' | 'PEBBLE' | 'SAND', string][] = [
    ['ROCK', 'Rocks'],
    ['PEBBLE', 'Pebbles'],
    ['SAND', 'Sand'],
  ];
  return (
    <div
      data-testid="team-rollup-tiers"
      className="flex gap-3 rounded border border-gray-200 bg-white p-3"
    >
      {tiers.map(([key, label]) => (
        <div key={key} className="flex flex-col">
          <span className="text-xs text-gray-500">{label}</span>
          <span className="text-base font-semibold text-gray-900">
            {tierDistribution[key] ?? 0}
          </span>
        </div>
      ))}
    </div>
  );
}

function percent(value: number | undefined): string {
  if (value == null) return '0%';
  return `${Math.round(value * 100)}%`;
}
