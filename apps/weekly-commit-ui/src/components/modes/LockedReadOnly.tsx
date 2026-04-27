import type { ReactNode } from 'react';
import { useListCommitsQuery, type WeeklyCommitResponse } from '@wc/rtk-api-client';
import { ChessTier } from '../ChessTier';
import { StateBadge } from '../StateBadge';
import { TIER_META } from '../icons/tierMeta';

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
      <div data-testid="locked-readonly-loading" className="text-slate-500">
        Loading…
      </div>
    );
  }
  if (error || !commits) {
    return (
      <div
        data-testid="locked-readonly-error"
        role="alert"
        className="rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-ink"
      >
        Couldn’t load commits.
      </div>
    );
  }

  return (
    <div data-testid="week-editor-locked-readonly" className="flex flex-col gap-6">
      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-soft-sm">
        <StateBadge state="LOCKED" isReconcileEligible={false} />
      </div>
      <ChessTier commits={commits} renderCommit={renderReadOnlyRow} />
    </div>
  );
}

function renderReadOnlyRow(commit: WeeklyCommitResponse): ReactNode {
  const meta = TIER_META[commit.chessTier];
  const Icon = meta.Icon;
  return (
    <div
      data-testid={`locked-row-${commit.id}`}
      className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-soft-sm"
    >
      <span
        className={`flex h-8 w-8 flex-none items-center justify-center rounded-md ${meta.chipBg} ${meta.ink}`}
        aria-hidden
      >
        <Icon className="h-4 w-4" />
      </span>
      <span className="flex-1 text-sm font-medium text-slate-900">{commit.title}</span>
      <span
        className="text-meta uppercase text-slate-500"
        data-testid={`locked-row-${commit.id}-status`}
      >
        {commit.actualStatus}
      </span>
    </div>
  );
}
