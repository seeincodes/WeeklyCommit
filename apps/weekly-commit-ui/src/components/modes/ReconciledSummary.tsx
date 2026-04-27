import type { ReactNode } from 'react';
import {
  useCarryForwardMutation,
  useListCommitsQuery,
  type WeeklyCommitResponse,
} from '@wc/rtk-api-client';
import { CarryAllButton, CarryForwardRow } from '../CarryForwardRow';
import { CarryStreakBadge } from '../CarryStreakBadge';
import { ChessTier } from '../ChessTier';
import { StateBadge } from '../StateBadge';
import { TIER_META } from '../icons/tierMeta';

interface ReconciledSummaryProps {
  planId: string;
  /**
   * The plan's reflection note. Passed through from `<WeekEditor />` so this
   * pane doesn't refetch the plan -- the caller already has it in scope.
   * Optional + nullable because the backend treats reflection as optional
   * and `WeeklyPlanResponse.reflectionNote` is `string | undefined`.
   */
  reflectionNote?: string | undefined;
}

/**
 * The RECONCILED-state pane of the WeekEditor. Also serves as the safe
 * degrade path for ARCHIVED plans. Renders a read-only commit list grouped
 * by `<ChessTier />`, surfaces the `<CarryStreakBadge />` for each row when
 * the streak crosses the >=2 threshold, displays the full reflection note
 * (when non-empty), and keeps carry-forward affordances available so the IC
 * can still carry MISSED/PARTIAL work into next week per [MVP6] (carry stays
 * available post-reconcile until next-week creation closes the door).
 */
export function ReconciledSummary({ planId, reflectionNote }: ReconciledSummaryProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });
  const [carryForward] = useCarryForwardMutation();

  if (isLoading) {
    return (
      <div data-testid="reconciled-loading" className="text-slate-500">
        Loading…
      </div>
    );
  }
  if (error || !commits) {
    return (
      <div
        data-testid="reconciled-error"
        role="alert"
        className="rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-ink"
      >
        Couldn’t load commits.
      </div>
    );
  }

  const renderRow = (commit: WeeklyCommitResponse): ReactNode => {
    const streak = commit.derived?.carryStreak ?? 0;
    const meta = TIER_META[commit.chessTier];
    const Icon = meta.Icon;
    return (
      <div
        data-testid={`reconciled-row-${commit.id}`}
        className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-soft-sm"
      >
        <span
          className={`flex h-8 w-8 flex-none items-center justify-center rounded-md ${meta.chipBg} ${meta.ink}`}
          aria-hidden
        >
          <Icon className="h-4 w-4" />
        </span>
        <span className="text-sm font-medium text-slate-900">{commit.title}</span>
        <span
          className="text-meta uppercase text-slate-500"
          data-testid={`reconciled-row-${commit.id}-status`}
        >
          {commit.actualStatus}
        </span>
        <CarryStreakBadge carryStreak={streak} />
        <span className="ml-auto">
          <CarryForwardRow commit={commit} onCarry={(id) => void carryForward({ commitId: id })} />
        </span>
      </div>
    );
  };

  const hasReflection = reflectionNote != null && reflectionNote !== '';

  return (
    <div data-testid="week-editor-reconciled" className="flex flex-col gap-6">
      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-soft-sm">
        <StateBadge state="RECONCILED" isReconcileEligible={false} />
      </div>
      {hasReflection && (
        <section
          data-testid="reconciled-reflection"
          className="rounded-xl border border-slate-200 bg-slate-50 p-4"
        >
          <h3 className="text-meta uppercase text-slate-500">Reflection</h3>
          <p className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{reflectionNote}</p>
        </section>
      )}
      <CarryAllButton
        commits={commits}
        onCarryAll={(ids) => {
          for (const id of ids) {
            void carryForward({ commitId: id });
          }
        }}
      />
      <ChessTier commits={commits} renderCommit={renderRow} />
    </div>
  );
}
