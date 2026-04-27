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
  pill: string;
  dot: string;
  hint: string | null;
}

function styleFor(state: PlanState, isReconcileEligible: boolean): BadgeStyle {
  switch (state) {
    case 'DRAFT':
      return {
        pill: 'bg-brand-soft text-brand-ink ring-brand/20',
        dot: 'bg-brand',
        hint: 'Lock the week when you’re done planning.',
      };
    case 'LOCKED':
      return {
        pill: 'bg-warn-soft text-warn-ink ring-warn/20',
        dot: 'bg-warn',
        hint: isReconcileEligible
          ? 'Reconcile each commit and submit reconciliation.'
          : 'Reconciliation opens Friday.',
      };
    case 'RECONCILED':
      return {
        pill: 'bg-ok-soft text-ok-ink ring-ok/20',
        dot: 'bg-ok',
        hint: 'Carry forward any missed work to next week.',
      };
    case 'ARCHIVED':
      return {
        pill: 'bg-slate-100 text-slate-600 ring-slate-300/40',
        dot: 'bg-slate-400',
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
 *
 * Visual: a colour-coded dot (matched to the state palette) leads the
 * label inside a soft pill with a 1px ring. The hint sits next to it as
 * mid-weight slate text. The leading dot gives the badge a glance-read
 * even before the label resolves -- DRAFT (indigo) / LOCKED (amber) /
 * RECONCILED (emerald) / ARCHIVED (slate) read by colour at the
 * peripheral-vision level.
 */
export function StateBadge({ state, isReconcileEligible }: StateBadgeProps) {
  const { pill, dot, hint } = styleFor(state, isReconcileEligible);
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span
        data-testid="state-badge"
        data-state={state}
        className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wide ring-1 ring-inset ${pill}`}
      >
        <span className={`h-1.5 w-1.5 rounded-full ${dot}`} aria-hidden />
        {state.charAt(0) + state.slice(1).toLowerCase()}
      </span>
      {hint != null && (
        <span data-testid="state-badge-hint" className="text-sm text-slate-600">
          {hint}
        </span>
      )}
    </div>
  );
}
