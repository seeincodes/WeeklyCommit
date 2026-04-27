import { useMemo } from 'react';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';
import { TIERS_ORDERED, TIER_META, type Tier } from './icons/tierMeta';
import { FlagIcon } from './icons';

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
 * Visual model: each tier section gets a tier-specific accent (warm amber
 * for Rock, sky for Pebble, stone for Sand) carried by a leading colour
 * rail, the tier glyph in the heading, a soft surface tint, and a tinted
 * count chip. The chess metaphor is *visualised*, not just labelled --
 * Rock reads heavier than Sand at a glance, which is the whole point of
 * the tiering system.
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
    for (const tier of TIERS_ORDERED) {
      buckets[tier].sort((a, b) => a.displayOrder - b.displayOrder);
    }
    return buckets;
  }, [commits]);

  // Top Rock = first ROCK by displayOrder (already sorted above), or undefined.
  const topRockId = grouped.ROCK[0]?.id;
  const noTopRock = grouped.ROCK.length === 0;

  return (
    <div className="flex flex-col gap-4" data-testid="chess-tier-spine">
      {noTopRock && <NoTopRockBanner />}
      {TIERS_ORDERED.map((tier) => (
        <TierSection
          key={tier}
          tier={tier}
          commits={grouped[tier]}
          topRockId={topRockId}
          renderCommit={renderCommit}
        />
      ))}
    </div>
  );
}

interface TierSectionProps {
  tier: Tier;
  commits: WeeklyCommitResponse[];
  topRockId: string | undefined;
  renderCommit: ChessTierProps['renderCommit'];
}

function TierSection({ tier, commits, topRockId, renderCommit }: TierSectionProps) {
  const meta = TIER_META[tier];
  const Icon = meta.Icon;
  // Heading lookup order in the existing test asserts on a single heading
  // per section (`getByRole('heading')`). Keep it as a single h2 -- the
  // count chip lives next to the heading, not as a second heading.
  return (
    <section
      role="group"
      aria-label={`${tier.toLowerCase()} tier`}
      className={`relative overflow-hidden rounded-xl border bg-white shadow-soft-sm ${meta.border}`}
    >
      <div className={`absolute inset-y-0 left-0 w-1 ${meta.rail}`} aria-hidden />
      <div className={`flex items-center justify-between gap-3 px-4 py-3 ${meta.surface}`}>
        <div className="flex items-center gap-2">
          <span
            className={`flex h-7 w-7 items-center justify-center rounded-md bg-white/70 ${meta.ink}`}
            aria-hidden
          >
            <Icon className="h-4 w-4" />
          </span>
          <h2 className={`text-sm font-semibold uppercase tracking-wide ${meta.ink}`}>
            {meta.label}
          </h2>
        </div>
        <span
          className={`inline-flex min-w-[1.75rem] items-center justify-center rounded-full px-2 py-0.5 text-xs font-semibold ${meta.chipBg} ${meta.chipText}`}
          aria-label={`${commits.length} ${meta.label.toLowerCase()} commits`}
        >
          {commits.length}
        </span>
      </div>
      <div className="px-4 py-3">
        {commits.length === 0 ? (
          <div
            data-testid={`chess-tier-empty-${tier}`}
            className="rounded-md border border-dashed border-slate-200 px-3 py-4 text-center text-sm italic text-slate-400"
          >
            No {tier.toLowerCase()} commits yet.
          </div>
        ) : (
          <ul className="flex flex-col gap-2">
            {commits.map((c) => (
              <li key={c.id}>{renderCommit(c, c.id === topRockId)}</li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function NoTopRockBanner() {
  return (
    <div
      data-testid="chess-tier-no-top-rock"
      role="status"
      className="flex items-start gap-3 rounded-lg border border-warn/30 bg-warn-soft px-4 py-3 text-sm text-warn-ink"
    >
      <FlagIcon className="mt-0.5 h-4 w-4 flex-none text-warn" aria-hidden />
      <p>
        <span className="font-semibold">No Top Rock yet.</span> Pick the highest-leverage commit and
        mark it as a Rock.
      </p>
    </div>
  );
}
