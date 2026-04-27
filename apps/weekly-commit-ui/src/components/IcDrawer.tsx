import { useEffect, useId } from 'react';
import { useGetPlanByEmployeeAndWeekQuery, useListCommitsQuery } from '@wc/rtk-api-client';
import type { WeeklyCommitResponse, WeeklyPlanResponse } from '@wc/rtk-api-client';
import { ChessTier } from './ChessTier';
import { CarryStreakBadge } from './CarryStreakBadge';
import { StateBadge } from './StateBadge';

interface IcDrawerProps {
  employeeId: string;
  /** Display name passed from the rollup (avoids a second fetch just to label the drawer). */
  employeeName: string;
  /** ISO date the rollup was queried for; the drawer fetches this employee's plan for that week. */
  weekStart: string;
  onClose: () => void;
}

/**
 * Manager IC deep-dive overlay per [MVP10] / USER_FLOW.md flow 4. Renders as
 * a `role="dialog"` overlay on top of the team-rollup view, preserving the
 * underlying context (the manager doesn't lose their place when they drill
 * into a member). Closes via:
 *
 *   - Escape keypress
 *   - click on the backdrop
 *   - explicit "Close" button (header)
 *
 * Inside-dialog clicks bubble normally but are stopped before reaching the
 * backdrop handler so a click on a commit row doesn't dismiss the drawer.
 *
 * Surfaces:
 *
 *   - Plan state badge (read-only -- the manager doesn't transition state
 *     on someone else's plan; comment-only review per MEMO #5).
 *   - Full reflection note (no truncation -- the rollup card showed a
 *     preview; the drawer is where the manager reads the whole thing).
 *   - Commit list grouped by chess tier via the existing <ChessTier />,
 *     each row decorated with a <CarryStreakBadge /> when applicable so
 *     streak chains are visible at a glance.
 *
 * The plan-level comment field (subtask 5) lands inside this drawer in a
 * follow-up commit; the slot is here today as a placeholder so the
 * scrolling layout is settled.
 */
export function IcDrawer({ employeeId, employeeName, weekStart, onClose }: IcDrawerProps) {
  const titleId = useId();

  // Escape closes the drawer no matter where focus is inside it.
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  const planQuery = useGetPlanByEmployeeAndWeekQuery({ employeeId, weekStart });
  const planId = planQuery.data?.id;
  const commitsQuery = useListCommitsQuery(planId != null ? { planId } : { planId: '' }, {
    skip: planId == null,
  });

  return (
    <div
      data-testid="ic-drawer-backdrop"
      onClick={onClose}
      className="fixed inset-0 z-50 flex justify-end bg-black/40"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        className="flex h-full w-full max-w-2xl flex-col gap-4 overflow-y-auto bg-white p-6 shadow-xl"
      >
        <header className="flex items-start justify-between">
          <h2 id={titleId} className="text-xl font-semibold text-gray-900">
            {employeeName}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded p-1 text-gray-500 hover:bg-gray-100"
          >
            ✕
          </button>
        </header>

        <Body
          plan={planQuery.data}
          planLoading={planQuery.isLoading}
          planError={planQuery.error != null}
          commits={commitsQuery.data ?? []}
        />
      </div>
    </div>
  );
}

function Body({
  plan,
  planLoading,
  planError,
  commits,
}: {
  plan: WeeklyPlanResponse | undefined;
  planLoading: boolean;
  planError: boolean;
  commits: WeeklyCommitResponse[];
}) {
  if (planLoading) {
    return (
      <div data-testid="ic-drawer-loading" className="text-sm text-gray-500">
        Loading plan…
      </div>
    );
  }
  if (planError || plan == null) {
    return (
      <div data-testid="ic-drawer-no-plan" className="text-sm italic text-gray-500">
        No plan for this employee in the selected week.
      </div>
    );
  }

  return (
    <>
      <div className="flex items-center gap-3">
        <StateBadge state={plan.state} isReconcileEligible={false} />
      </div>

      <ReflectionBlock note={plan.reflectionNote} />

      <section className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold uppercase text-gray-600">Commits</h3>
        <ChessTier commits={commits} renderCommit={(c) => <CommitRow commit={c} />} />
      </section>
    </>
  );
}

function ReflectionBlock({ note }: { note: string | null | undefined }) {
  if (note == null || note === '') {
    return (
      <div data-testid="ic-drawer-no-reflection" className="text-sm italic text-gray-500">
        No reflection note yet.
      </div>
    );
  }
  return (
    <section className="flex flex-col gap-1">
      <h3 className="text-sm font-semibold uppercase text-gray-600">Reflection</h3>
      <p data-testid="ic-drawer-reflection" className="whitespace-pre-wrap text-sm text-gray-800">
        {note}
      </p>
    </section>
  );
}

function CommitRow({ commit }: { commit: WeeklyCommitResponse }) {
  // Only show the streak badge when carryStreak >= 2 (CarryStreakBadge handles the threshold;
  // we still gate the surrounding flex spacing so a 0-streak row doesn't show a phantom gap).
  const streak = commit.derived?.carryStreak ?? 0;
  return (
    <div className="flex items-center justify-between gap-3 rounded border border-gray-200 px-3 py-2">
      <span className="text-sm text-gray-900">{commit.title}</span>
      {streak >= 2 && <CarryStreakBadge carryStreak={streak} />}
    </div>
  );
}
