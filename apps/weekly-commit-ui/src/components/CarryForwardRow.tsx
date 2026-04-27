import type { WeeklyCommitResponse } from '@wc/rtk-api-client';

/**
 * A commit is carry-forward eligible per [MVP6] when it ended the week
 * MISSED or PARTIAL and hasn't already been carried (carriedForwardToId
 * not yet set). DONE and PENDING never qualify -- DONE is finished work,
 * PENDING means reconciliation hasn't been written yet.
 */
function isCarryEligible(commit: WeeklyCommitResponse): boolean {
  if (commit.carriedForwardToId != null) return false;
  return commit.actualStatus === 'MISSED' || commit.actualStatus === 'PARTIAL';
}

interface CarryForwardRowProps {
  commit: WeeklyCommitResponse;
  /** Fired with the source commit id; parent calls POST /commits/{id}/carry-forward. */
  onCarry: (commitId: string) => void;
}

/**
 * Per-commit carry-forward affordance. Renders the action button only when
 * the commit is carry-eligible; once already carried, surfaces a small
 * "already carried" indicator instead so the row can't be double-carried.
 */
export function CarryForwardRow({ commit, onCarry }: CarryForwardRowProps) {
  if (commit.carriedForwardToId != null) {
    return (
      <span data-testid="carry-forward-already-done" className="text-xs italic text-gray-500">
        Already carried to next week.
      </span>
    );
  }
  if (!isCarryEligible(commit)) return null;
  return (
    <button
      type="button"
      onClick={() => onCarry(commit.id)}
      className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-700 hover:bg-gray-50"
    >
      Carry to next week
    </button>
  );
}

interface CarryAllButtonProps {
  commits: WeeklyCommitResponse[];
  /** Fired with the list of carry-eligible commit ids. */
  onCarryAll: (commitIds: string[]) => void;
}

/**
 * Bulk "carry all missed/partial" affordance. Disabled when no commit in
 * the plan qualifies (e.g. the IC reconciled everything DONE). Per [MVP6]
 * this is a single click that creates twins for every eligible row.
 */
export function CarryAllButton({ commits, onCarryAll }: CarryAllButtonProps) {
  const eligibleIds = commits.filter(isCarryEligible).map((c) => c.id);
  const disabled = eligibleIds.length === 0;
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onCarryAll(eligibleIds)}
      className="rounded border border-gray-400 bg-white px-3 py-1 text-sm font-medium text-gray-800 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
    >
      Carry all missed/partial to next week
    </button>
  );
}
