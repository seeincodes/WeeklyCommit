import type { ReactNode } from 'react';
import { useListCommitsQuery, type WeeklyCommitResponse } from '@wc/rtk-api-client';
import { ChessTier } from '../ChessTier';
import { StateBadge } from '../StateBadge';

interface LockedReadOnlyProps {
  planId: string;
}

/**
 * The LOCKED-pre-day-4 pane of the WeekEditor. Read-only commit list grouped
 * by ChessTier with a StateBadge that surfaces the "Reconciliation opens
 * Friday" hint. Reconcile-eligibility is computed by the parent (WeekEditor)
 * and a plan that is reconcile-eligible routes to ReconcileMode instead of
 * here, so we can hardcode `isReconcileEligible={false}`.
 */
export function LockedReadOnly({ planId }: LockedReadOnlyProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });

  if (isLoading) {
    return (
      <div data-testid="locked-readonly-loading" className="text-sm text-gray-500">
        Loading the locked week…
      </div>
    );
  }
  if (error || !commits) {
    return (
      <div data-testid="locked-readonly-error" role="alert" className="text-sm text-red-700">
        Couldn’t load commits. Try refreshing the page.
      </div>
    );
  }

  return (
    <div data-testid="week-editor-locked-readonly" className="flex flex-col gap-4">
      <StateBadge state="LOCKED" isReconcileEligible={false} />
      <ChessTier commits={commits} renderCommit={renderReadOnlyRow} />
    </div>
  );
}

function renderReadOnlyRow(commit: WeeklyCommitResponse, _isTopRock: boolean): ReactNode {
  return (
    <div
      data-testid={`locked-row-${commit.id}`}
      className="flex items-center gap-3 px-3 py-2 border-b last:border-b-0"
    >
      <span className="font-medium text-gray-900">{commit.title}</span>
      <span
        className="ml-auto text-xs uppercase tracking-wide text-gray-500"
        data-testid={`locked-row-${commit.id}-status`}
      >
        {commit.actualStatus}
      </span>
    </div>
  );
}
