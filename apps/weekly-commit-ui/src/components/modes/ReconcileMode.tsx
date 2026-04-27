interface ReconcileModeProps {
  planId: string;
}

/**
 * The LOCKED-post-day-4 reconciliation pane of the WeekEditor. Group 13b
 * subtask 3 wires the ReconcileTable + ReflectionField + Submit + carry-forward
 * affordances here.
 */
export function ReconcileMode({ planId: _planId }: ReconcileModeProps) {
  return <div data-testid="week-editor-reconcile">Reconciliation (subtasks 5-7 land here).</div>;
}
