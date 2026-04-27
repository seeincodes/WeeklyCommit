import type { KeyboardEvent } from 'react';
import type { MemberCard as MemberCardType } from '@wc/rtk-api-client';
import { ChevronRightIcon, FlagIcon } from './icons';
import { TIERS_ORDERED, TIER_META } from './icons/tierMeta';

interface MemberCardProps {
  member: MemberCardType;
  /** Fired with the employee id when the card is clicked / Enter / Space. */
  onClick: (employeeId: string) => void;
}

const PREVIEW_MAX = 80;

/**
 * One row in the manager team rollup. Shows the at-a-glance signals per
 * USER_FLOW.md flow 4 + [MVP9]:
 *
 *   - name + plan state pill
 *   - Top Rock (or "no Top Rock" indicator)
 *   - tier shape (Rock/Pebble/Sand counts as compact tinted chips)
 *   - reflection preview (truncated to 80 chars, italic)
 *   - flag chips (UNREVIEWED_72H, STUCK_COMMIT, NO_TOP_ROCK, ...) coloured
 *     by severity so a glanced row signals "needs attention" before any
 *     text resolves
 *
 * Visual model: a leading status rail (warn for unreviewed, danger for
 * stuck, slate for clean) lets a manager skim the column for action
 * items. The whole card is `role="button"` so keyboard users can drill
 * into the IC drawer with Enter / Space (matches the existing test
 * contract). The chevron at the trailing edge says "drill in" without
 * forcing a textual link.
 */
export function MemberCard({ member, onClick }: MemberCardProps) {
  const handleActivate = () => onClick(member.employeeId ?? '');
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleActivate();
    }
  };

  const flags = member.flags ?? [];
  const railTone = flags.length === 0 ? 'bg-slate-200' : railColourFor(flags);

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={handleActivate}
      onKeyDown={onKeyDown}
      aria-label={member.name}
      data-testid="member-card"
      className="group relative cursor-pointer overflow-hidden rounded-xl border border-slate-200 bg-white shadow-soft-sm transition-all hover:-translate-y-0.5 hover:border-brand/30 hover:shadow-soft focus:outline-none focus:ring-2 focus:ring-brand/40"
    >
      <div className={`absolute inset-y-0 left-0 w-1 ${railTone}`} aria-hidden />
      <div className="flex items-start gap-3 px-4 py-3 pl-5">
        <div className="flex flex-1 flex-col gap-2">
          <div className="flex items-start justify-between gap-3">
            <div className="flex flex-col">
              <span className="text-base font-semibold text-slate-900">{member.name}</span>
              <span data-testid="member-card-state" className="text-meta uppercase text-slate-500">
                {(member.planState ?? '').toLowerCase()}
              </span>
            </div>
            <TierShape counts={member.tierCounts ?? {}} />
          </div>

          <TopRockLine title={member.topRock?.title} />

          {member.reflectionPreview != null && member.reflectionPreview !== '' && (
            <p
              data-testid="member-card-reflection"
              className="border-l-2 border-slate-200 pl-3 text-sm italic text-slate-600"
            >
              {truncate(member.reflectionPreview, PREVIEW_MAX)}
            </p>
          )}

          {flags.length > 0 && (
            <div data-testid="member-card-flags" className="flex flex-wrap gap-1.5">
              {flags.map((flag) => (
                <Flag key={flag} flag={flag} />
              ))}
            </div>
          )}
        </div>
        <ChevronRightIcon
          className="mt-1 h-4 w-4 flex-none text-slate-300 transition-colors group-hover:text-brand"
          aria-hidden
        />
      </div>
    </div>
  );
}

function TopRockLine({ title }: { title: string | undefined }) {
  if (title == null || title === '') {
    return (
      <div
        data-testid="member-card-no-top-rock"
        className="inline-flex items-center gap-1.5 self-start rounded-md bg-warn-soft px-2 py-0.5 text-xs font-medium text-warn-ink"
      >
        <FlagIcon className="h-3 w-3" aria-hidden />
        No Top Rock
      </div>
    );
  }
  return (
    <div className="flex items-baseline gap-1.5">
      <span className="text-meta uppercase text-slate-500">Top Rock</span>
      <span data-testid="member-card-top-rock" className="text-sm font-medium text-slate-900">
        {title}
      </span>
    </div>
  );
}

function TierShape({ counts }: { counts: Record<string, number> }) {
  return (
    <div data-testid="member-card-tiers" className="flex gap-1.5">
      {TIERS_ORDERED.map((t) => {
        const meta = TIER_META[t];
        const count = counts[t] ?? 0;
        const muted = count === 0;
        return (
          <span
            key={t}
            title={`${meta.label}: ${count}`}
            className={`inline-flex h-7 min-w-[2.25rem] items-center justify-center gap-1 rounded-md px-1.5 text-xs font-semibold ${
              muted ? 'bg-slate-100 text-slate-400' : `${meta.chipBg} ${meta.chipText}`
            }`}
          >
            <span className={`text-[10px] font-bold ${muted ? '' : 'opacity-80'}`}>
              {meta.label[0]}
            </span>
            <span>{count}</span>
          </span>
        );
      })}
    </div>
  );
}

function Flag({ flag }: { flag: string }) {
  const tone = flagTone(flag);
  return (
    <span
      data-testid={`flag-${flag}`}
      className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ${tone}`}
    >
      <FlagIcon className="h-3 w-3" aria-hidden />
      {humanFlag(flag)}
    </span>
  );
}

function railColourFor(flags: string[]): string {
  // Severity rule of thumb: stuck > unreviewed > everything else. The rail is
  // the strongest visual signal on the card -- a manager scanning a column
  // wants the loudest colour on the rows that need them most.
  if (flags.includes('STUCK_COMMIT')) return 'bg-danger';
  if (flags.includes('UNREVIEWED_72H')) return 'bg-warn';
  return 'bg-slate-300';
}

function flagTone(flag: string): string {
  switch (flag) {
    case 'STUCK_COMMIT':
      return 'bg-danger-soft text-danger-ink';
    case 'UNREVIEWED_72H':
      return 'bg-warn-soft text-warn-ink';
    case 'NO_TOP_ROCK':
      return 'bg-warn-soft text-warn-ink';
    case 'DRAFT_WITH_UNLINKED':
      return 'bg-slate-100 text-slate-700';
    default:
      return 'bg-slate-100 text-slate-700';
  }
}

function humanFlag(flag: string): string {
  // Replace underscores with spaces and lower-case for readability. The
  // existing test asserts on the test id, not the visible text, so the
  // copy can change here without regression.
  return flag.replace(/_/g, ' ').toLowerCase();
}

function truncate(text: string, max: number): string {
  if (text.length <= max) return text;
  return text.slice(0, max) + '…';
}
