import type { KeyboardEvent } from 'react';
import { Tooltip } from 'flowbite-react';
import type { MemberCard as MemberCardType } from '@wc/rtk-api-client';

interface MemberCardProps {
  member: MemberCardType;
  /** Fired with the employee id when the card is clicked / Enter / Space. */
  onClick: (employeeId: string) => void;
}

const PREVIEW_MAX = 80;

/**
 * Human copy for each flag code the rollup service emits. The label is what
 * the user sees on the badge; the reason is the tooltip body that explains
 * what triggered it. Keep these grounded in the backend's `RollupService.computeFlags`
 * so the explanation matches the actual rule.
 */
interface FlagCopy {
  label: string;
  reason: string;
}
const FLAG_COPY: Record<string, FlagCopy> = {
  UNREVIEWED_72H: {
    label: 'Unreviewed 72h',
    reason: 'Reconciled more than 72 hours ago and you haven’t commented yet.',
  },
  STUCK_COMMIT: {
    label: 'Stuck commit',
    reason: 'A commit on this plan has been carried forward 3+ weeks in a row.',
  },
  NO_TOP_ROCK: {
    label: 'No Top Rock',
    reason: 'No Rock-tier commit on this plan — there’s no clear weekly priority.',
  },
  DRAFT_WITH_UNLINKED: {
    label: 'Empty draft',
    reason: 'Plan is in DRAFT but has no commits yet — the IC may need a nudge.',
  },
};

function copyFor(flag: string): FlagCopy {
  return FLAG_COPY[flag] ?? { label: humanizeFallback(flag), reason: 'No additional context.' };
}

function humanizeFallback(flag: string): string {
  return flag
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * One row in the manager team rollup. Shows the at-a-glance signals per
 * USER_FLOW.md flow 4 + [MVP9]:
 *
 *   - name + plan state
 *   - Top Rock (or "no Top Rock" indicator)
 *   - tier shape (Rock/Pebble/Sand counts as compact pills)
 *   - reflection preview (truncated to 80 chars)
 *   - flag badges (UNREVIEWED_72H, STUCK_COMMIT, NO_TOP_ROCK, ...)
 *
 * The whole row is a `role="button"` so keyboard users can drill into the
 * IC drawer (subtask 4) with Enter/Space. onClick hands the parent the
 * employee id; the parent decides whether to navigate, open a drawer
 * overlay, or both.
 */
export function MemberCard({ member, onClick }: MemberCardProps) {
  const handleActivate = () => onClick(member.employeeId ?? '');
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleActivate();
    }
  };

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={handleActivate}
      onKeyDown={onKeyDown}
      aria-label={member.name}
      data-testid="member-card"
      className="cursor-pointer rounded border border-gray-200 bg-white p-3 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col">
          <span className="text-base font-semibold text-gray-900">{member.name}</span>
          <span data-testid="member-card-state" className="text-xs uppercase text-gray-500">
            {(member.planState ?? '').toLowerCase()}
          </span>
        </div>
        <TierShape counts={member.tierCounts ?? {}} />
      </div>

      <TopRockLine title={member.topRock?.title} />

      {member.reflectionPreview != null && member.reflectionPreview !== '' && (
        <p data-testid="member-card-reflection" className="mt-2 text-sm italic text-gray-700">
          {truncate(member.reflectionPreview, PREVIEW_MAX)}
        </p>
      )}

      {member.flags != null && member.flags.length > 0 && (
        <div data-testid="member-card-flags" className="mt-2 flex flex-wrap gap-1">
          {member.flags.map((flag) => (
            <Flag key={flag} flag={flag} />
          ))}
        </div>
      )}
    </div>
  );
}

function TopRockLine({ title }: { title: string | undefined }) {
  if (title == null || title === '') {
    return (
      <div data-testid="member-card-no-top-rock" className="mt-1 text-xs text-orange-700">
        No Top Rock
      </div>
    );
  }
  return (
    <div className="mt-1 flex items-baseline gap-1">
      <span className="text-xs uppercase text-gray-500">Top Rock:</span>
      <span data-testid="member-card-top-rock" className="text-sm font-medium text-gray-900">
        {title}
      </span>
    </div>
  );
}

function TierShape({ counts }: { counts: Record<string, number> }) {
  return (
    <div data-testid="member-card-tiers" className="flex gap-2 text-xs text-gray-600">
      <span title="Rocks">R {counts.ROCK ?? 0}</span>
      <span title="Pebbles">P {counts.PEBBLE ?? 0}</span>
      <span title="Sand">S {counts.SAND ?? 0}</span>
    </div>
  );
}

function Flag({ flag }: { flag: string }) {
  const { label, reason } = copyFor(flag);
  return (
    <Tooltip
      content={reason}
      placement="top"
      // The badge is interactive (focusable) so keyboard users can hover-equivalent
      // by tabbing in. The tooltip's accessible name comes from the badge content;
      // the reason copy is announced by Flowbite's underlying `<Tooltip>` ARIA
      // wiring.
      style="dark"
    >
      <span
        data-testid={`flag-${flag}`}
        tabIndex={0}
        // Stop the click from bubbling up to the row's onClick (which would
        // open the drawer). Hovering/clicking the flag should reveal the
        // tooltip without a navigation side-effect.
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.stopPropagation();
          }
        }}
        className="inline-flex items-center rounded bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-200 cursor-help"
      >
        {label}
      </span>
    </Tooltip>
  );
}

function truncate(text: string, max: number): string {
  if (text.length <= max) return text;
  return text.slice(0, max) + '…';
}
