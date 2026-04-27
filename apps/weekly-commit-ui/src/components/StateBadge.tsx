type PlanState = 'DRAFT' | 'LOCKED' | 'RECONCILED' | 'ARCHIVED';

interface StateBadgeProps {
  state: PlanState;
  /**
   * Whether the plan is past `weekStart + 4 days` and so reconciliation
   * mode is open. Computed by the parent (the `<WeekEditor />` already has
   * the predicate; subtask 10's TZ helper centralizes it). Passed in
   * rather than recomputed here to keep this component pure-presentational.
   */
  isReconcileEligible: boolean;
}

interface BadgeStyle {
  className: string;
  hint: string | null;
}

function styleFor(state: PlanState, isReconcileEligible: boolean): BadgeStyle {
  switch (state) {
    case 'DRAFT':
      return {
        className: 'bg-blue-100 text-blue-800',
        hint: 'Lock the week when you’re done planning.',
      };
    case 'LOCKED':
      return {
        className: 'bg-yellow-100 text-yellow-800',
        hint: isReconcileEligible
          ? 'Reconcile each commit and submit reconciliation.'
          : 'Reconciliation opens Friday.',
      };
    case 'RECONCILED':
      return {
        className: 'bg-green-100 text-green-800',
        hint: 'Carry forward any missed work to next week.',
      };
    case 'ARCHIVED':
      return {
        className: 'bg-gray-100 text-gray-700',
        hint: null,
      };
  }
}

/**
 * Plan state pill + next-action hint, surfaced near the page heading. The
 * hint maps state x reconcile-eligibility -> a one-line user-facing nudge
 * derived from USER_FLOW.md flows 1-3 (e.g. "Lock the week" in DRAFT,
 * "Reconciliation opens Friday" in pre-day-4 LOCKED, "Submit
 * reconciliation" in post-day-4 LOCKED, "Carry forward" in RECONCILED).
 * ARCHIVED is a terminal state with no action.
 */
export function StateBadge({ state, isReconcileEligible }: StateBadgeProps) {
  const { className, hint } = styleFor(state, isReconcileEligible);
  return (
    <div className="flex items-center gap-2">
      <span
        data-testid="state-badge"
        data-state={state}
        className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${className}`}
      >
        {state.charAt(0) + state.slice(1).toLowerCase()}
      </span>
      {hint != null && (
        <span data-testid="state-badge-hint" className="text-xs text-gray-600">
          {hint}
        </span>
      )}
    </div>
  );
}
