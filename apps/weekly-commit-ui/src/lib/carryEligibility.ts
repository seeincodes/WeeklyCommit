import type { WeeklyCommitResponse } from '@wc/rtk-api-client';

/**
 * A commit is carry-forward eligible per [MVP6] when it ended the week
 * MISSED or PARTIAL and hasn't already been carried (carriedForwardToId
 * not yet set). DONE and PENDING never qualify -- DONE is finished work,
 * PENDING means reconciliation hasn't been written yet.
 *
 * Lives outside the carry-forward UI components so other modes (ReconcileMode,
 * ReconciledSummary) can filter without dragging the components into a
 * react-refresh lint conflict (component files can only export components).
 */
export function isCarryEligible(commit: WeeklyCommitResponse): boolean {
  if (commit.carriedForwardToId != null) return false;
  return commit.actualStatus === 'MISSED' || commit.actualStatus === 'PARTIAL';
}
