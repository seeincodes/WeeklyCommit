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
      <div data-testid="reconciled-loading" className="text-sm text-gray-500">
        Loading the reconciled week…
      </div>
    );
  }
  if (error || !commits) {
    return (
      <div data-testid="reconciled-error" role="alert" className="text-sm text-red-700">
        Couldn’t load commits. Try refreshing the page.
      </div>
    );
  }

  const renderRow = (commit: WeeklyCommitResponse, _isTopRock: boolean): ReactNode => {
    const streak = commit.derived?.carryStreak ?? 0;
    return (
      <div
        data-testid={`reconciled-row-${commit.id}`}
        className="flex items-center gap-3 px-3 py-2 border-b last:border-b-0"
      >
        <span className="font-medium text-gray-900">{commit.title}</span>
        <span
          className="text-xs uppercase tracking-wide text-gray-500"
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
    <div data-testid="week-editor-reconciled" className="flex flex-col gap-4">
      <StateBadge state="RECONCILED" isReconcileEligible={false} />
      {hasReflection && (
        <section
          data-testid="reconciled-reflection"
          className="rounded border border-gray-200 bg-gray-50 p-3"
        >
          <h3 className="text-sm font-semibold text-gray-700 mb-1">Reflection</h3>
          <p className="whitespace-pre-wrap text-gray-900">{reflectionNote}</p>
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
