interface ReconciledSummaryProps {
  planId: string;
}

/**
 * The RECONCILED-state pane of the WeekEditor. Also serves as the safe degrade
 * path for ARCHIVED plans. Group 13b subtask 4 wires the read-only commit list +
 * CarryStreakBadge + carry-forward affordances + full reflection here.
 */
export function ReconciledSummary({ planId: _planId }: ReconciledSummaryProps) {
  return <div data-testid="week-editor-reconciled">Week reconciled.</div>;
}
