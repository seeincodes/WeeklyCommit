import { useMemo } from 'react';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';

type Tier = 'ROCK' | 'PEBBLE' | 'SAND';
const TIERS: Tier[] = ['ROCK', 'PEBBLE', 'SAND'];

interface ChessTierProps {
  commits: WeeklyCommitResponse[];
  /**
   * Renders a single commit row inside its tier section. The container is
   * presentational -- it doesn't know how a commit should look in DRAFT vs
   * RECONCILE mode. The parent passes a renderer; this component groups,
   * sorts, and decorates with the Top Rock signal.
   *
   * `isTopRock` is true for exactly one commit per render: the lowest
   * `displayOrder` ROCK commit. It is always false for non-ROCK tiers
   * (Top Rock is ROCK-only per MEMO #7).
   */
  renderCommit: (commit: WeeklyCommitResponse, isTopRock: boolean) => React.ReactNode;
}

/**
 * Vertical "spine" layout for the chess metaphor: ROCK > PEBBLE > SAND, each
 * tier a `role="group"` section. Within a tier, commits sort by
 * `displayOrder` ascending so drag-reorder maps naturally.
 *
 * The Top Rock signal is computed here so every consumer (editor, manager
 * card, IC drawer) gets the same authoritative rule -- per MEMO decision
 * #7 Top Rock is *derived*, not stored. "No Top Rock" surfaces as an
 * empty-ROCK marker `chess-tier-no-top-rock` -- managers care about that
 * specifically.
 */
export function ChessTier({ commits, renderCommit }: ChessTierProps) {
  const grouped = useMemo(() => {
    const buckets: Record<Tier, WeeklyCommitResponse[]> = { ROCK: [], PEBBLE: [], SAND: [] };
    for (const c of commits) {
      buckets[c.chessTier].push(c);
    }
    for (const tier of TIERS) {
      buckets[tier].sort((a, b) => a.displayOrder - b.displayOrder);
    }
    return buckets;
  }, [commits]);

  // Top Rock = first ROCK by displayOrder (already sorted above), or undefined.
  const topRockId = grouped.ROCK[0]?.id;
  const noTopRock = grouped.ROCK.length === 0;

  return (
    <div className="flex flex-col gap-4" data-testid="chess-tier-spine">
      {noTopRock && (
        <div
          data-testid="chess-tier-no-top-rock"
          role="status"
          className="rounded border border-orange-300 bg-orange-50 px-3 py-2 text-sm text-orange-800"
        >
          No Top Rock yet — pick the highest-leverage commit and mark it as a Rock.
        </div>
      )}
      {TIERS.map((tier) => (
        <section
          key={tier}
          role="group"
          aria-label={`${tier.toLowerCase()} tier`}
          className={tierSectionClasses(tier)}
        >
          <h2 className="text-base font-semibold capitalize text-gray-800">{tier.toLowerCase()}</h2>
          {grouped[tier].length === 0 ? (
            <div data-testid={`chess-tier-empty-${tier}`} className="text-sm italic text-gray-400">
              No {tier.toLowerCase()} commits yet.
            </div>
          ) : (
            <ul className="flex flex-col gap-2">
              {grouped[tier].map((c) => (
                <li key={c.id}>{renderCommit(c, c.id === topRockId)}</li>
              ))}
            </ul>
          )}
        </section>
      ))}
    </div>
  );
}

function tierSectionClasses(tier: Tier): string {
  // Visual hierarchy mirrors the chess metaphor: rocks sit largest at the top,
  // sand smallest at the bottom. Border weight + padding scale accordingly.
  switch (tier) {
    case 'ROCK':
      return 'rounded-lg border-2 border-gray-700 p-4 flex flex-col gap-2 bg-white';
    case 'PEBBLE':
      return 'rounded-lg border border-gray-500 p-3 flex flex-col gap-2 bg-white';
    case 'SAND':
      return 'rounded border border-gray-300 p-2 flex flex-col gap-2 bg-white';
  }
}
