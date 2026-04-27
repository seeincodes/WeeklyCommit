import { useMemo } from 'react';
import type { MemberCard, RollupResponse } from '@wc/rtk-api-client';
import { CheckCircleIcon, FlagIcon } from './icons';
import { TIERS_ORDERED, TIER_META } from './icons/tierMeta';

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
 * Manager team rollup container per [MVP9]. Three structural pieces:
 *
 *   - Headline stats: alignment % and completion % rendered as large
 *     numerals + a thin progress bar so the manager reads "I'm on track"
 *     vs "I have a problem" before the numbers register. Backed by the
 *     ratio fields the backend already computes.
 *   - Stats strip: unreviewed count and stuck commit count as compact
 *     metric cards, each with a glyph (flag for unreviewed, rotating
 *     spark for stuck) and contextual colour (warn / danger).
 *   - Tier shape strip: the team's combined tier mix as a horizontal
 *     stacked bar with leader counts beneath. Reads as "this team is
 *     mostly Pebbles" at a glance, which is the actionable signal -- a
 *     team with no Rocks isn't planning at the right altitude.
 *   - Ordered member list. Sort key is "flagged-first then alphabetical":
 *     members with at least one flag float to the top so the manager's
 *     attention lands on the rows that need it. Within each bucket the
 *     order is alphabetical by name.
 *
 * Empty state preserves `team-rollup-empty` testid + the "no direct
 * reports" copy from the previous implementation; the redesigned empty
 * state shipped from EmptyState.tsx in a later commit replaces just the
 * visual treatment.
 */
export function TeamRollup({ rollup, renderMember }: TeamRollupProps) {
  const ordered = useMemo(() => orderMembers(rollup.members ?? []), [rollup.members]);

  return (
    <div className="flex flex-col gap-4" data-testid="team-rollup">
      <HeadlineStats rollup={rollup} />
      <TierStrip tierDistribution={rollup.tierDistribution ?? {}} />
      {ordered.length === 0 ? (
        <div
          data-testid="team-rollup-empty"
          className="rounded-lg border border-dashed border-slate-300 bg-white p-10 text-center text-sm italic text-slate-500"
        >
          No direct reports for this week.
        </div>
      ) : (
        <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2">
          {ordered.map((m) => renderMember(m))}
        </ul>
      )}
    </div>
  );
}

function orderMembers(members: MemberCard[]): MemberCard[] {
  return [...members].sort((a, b) => {
    const aFlagged = (a.flags?.length ?? 0) > 0;
    const bFlagged = (b.flags?.length ?? 0) > 0;
    if (aFlagged !== bFlagged) return aFlagged ? -1 : 1;
    return (a.name ?? '').localeCompare(b.name ?? '');
  });
}

function HeadlineStats({ rollup }: { rollup: RollupResponse }) {
  const alignment = pctNumber(rollup.alignmentPct);
  const completion = pctNumber(rollup.completionPct);
  const unreviewed = rollup.unreviewedCount ?? 0;
  const stuck = rollup.stuckCommitCount ?? 0;
  return (
    <div
      data-testid="team-rollup-stats"
      className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4"
    >
      <BigMetricCard
        label="Alignment"
        value={`${alignment}%`}
        progressPct={alignment}
        accent="brand"
        hint={alignmentHint(alignment)}
      />
      <BigMetricCard
        label="Completion"
        value={`${completion}%`}
        progressPct={completion}
        accent="ok"
        hint={completionHint(completion)}
      />
      <CountCard
        label="Unreviewed"
        value={unreviewed}
        icon={<FlagIcon className="h-4 w-4" />}
        accent="warn"
      />
      <CountCard
        label="Stuck commits"
        value={stuck}
        icon={<CheckCircleIcon className="h-4 w-4" />}
        accent="danger"
      />
    </div>
  );
}

interface BigMetricCardProps {
  label: string;
  value: string;
  /** 0-100 integer for the progress bar fill. */
  progressPct: number;
  accent: 'brand' | 'ok';
  hint?: string;
}

function BigMetricCard({ label, value, progressPct, accent, hint }: BigMetricCardProps) {
  // Tailwind needs literal class names so the JIT can find them; map the
  // accent string to concrete classes here rather than building them with
  // template strings.
  const fill = accent === 'brand' ? 'bg-brand' : 'bg-ok';
  const labelInk = accent === 'brand' ? 'text-brand-ink' : 'text-ok-ink';
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-soft-sm">
      <div className="flex items-baseline justify-between gap-2">
        <span className={`text-meta uppercase ${labelInk}`}>{label}</span>
        <span className="text-3xl font-semibold tracking-tight text-slate-900">{value}</span>
      </div>
      <div
        className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-label={`${label} ${value}`}
        aria-valuenow={progressPct}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className={`${fill} h-full rounded-full transition-all duration-300`}
          style={{ width: `${progressPct}%` }}
        />
      </div>
      {hint != null && hint !== '' && <p className="mt-2 text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

interface CountCardProps {
  label: string;
  value: number;
  icon: React.ReactNode;
  accent: 'warn' | 'danger';
}

function CountCard({ label, value, icon, accent }: CountCardProps) {
  // Tone the metric down when the count is zero (positive case): full colour
  // when there's something to act on, slate when there isn't. Avoids the
  // dashboard always glowing with "alert" colours regardless of state.
  const positive = value > 0;
  const tone = positive
    ? accent === 'warn'
      ? 'text-warn-ink bg-warn-soft border-warn/30'
      : 'text-danger-ink bg-danger-soft border-danger/30'
    : 'text-slate-500 bg-white border-slate-200';
  const iconTone = positive ? (accent === 'warn' ? 'text-warn' : 'text-danger') : 'text-slate-400';
  return (
    <div className={`rounded-xl border p-4 shadow-soft-sm ${tone}`}>
      <div className="flex items-center justify-between">
        <span className="text-meta uppercase opacity-80">{label}</span>
        <span className={iconTone}>{icon}</span>
      </div>
      <span className="mt-1 block text-3xl font-semibold tracking-tight">{value}</span>
    </div>
  );
}

function TierStrip({ tierDistribution }: { tierDistribution: Record<string, number> }) {
  const counts = TIERS_ORDERED.map((t) => tierDistribution[t] ?? 0);
  const total = counts.reduce((a, b) => a + b, 0);
  return (
    <section
      data-testid="team-rollup-tiers"
      className="rounded-xl border border-slate-200 bg-white p-4 shadow-soft-sm"
    >
      <header className="flex items-baseline justify-between">
        <h2 className="text-meta uppercase text-slate-500">Tier shape</h2>
        <span className="text-xs text-slate-500">{total} commits planned</span>
      </header>
      {total === 0 ? (
        <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-slate-100" aria-hidden />
      ) : (
        <div
          className="mt-3 flex h-2.5 w-full overflow-hidden rounded-full bg-slate-100"
          aria-hidden
        >
          {TIERS_ORDERED.map((t, i) => {
            const count = counts[i] ?? 0;
            if (count === 0) return null;
            const meta = TIER_META[t];
            const pct = (count / total) * 100;
            return (
              <div
                key={t}
                className={meta.rail}
                style={{ width: `${pct}%` }}
                title={`${meta.label}: ${count}`}
              />
            );
          })}
        </div>
      )}
      <ul className="mt-3 grid grid-cols-3 gap-2">
        {TIERS_ORDERED.map((t, i) => {
          const count = counts[i] ?? 0;
          const meta = TIER_META[t];
          const Icon = meta.Icon;
          return (
            <li
              key={t}
              className="flex items-center justify-between rounded-md border border-slate-100 px-2.5 py-1.5"
            >
              <span className={`flex items-center gap-1.5 text-sm font-medium ${meta.ink}`}>
                <Icon className="h-3.5 w-3.5" />
                {meta.label}
              </span>
              <span className="text-base font-semibold text-slate-900">{count}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/**
 * Server emits these as 0..1 BigDecimals serialized as numbers; we surface
 * 0..100 integers in the UI. Coerce undefined / null to 0 so the layout is
 * stable for empty rollups.
 */
function pctNumber(value: number | undefined): number {
  if (value == null) return 0;
  return Math.round(value * 100);
}

function alignmentHint(value: number): string {
  if (value === 0) return 'Plans this week.';
  if (value >= 90) return 'Strong: nearly every plan has a Top Rock.';
  if (value >= 60) return 'Most plans have a Top Rock.';
  return 'Several plans are missing a Top Rock.';
}

function completionHint(value: number): string {
  if (value === 0) return 'Reconciliation hasn’t started.';
  if (value >= 90) return 'Strong follow-through.';
  if (value >= 60) return 'On track.';
  return 'Many commits unfinished.';
}
