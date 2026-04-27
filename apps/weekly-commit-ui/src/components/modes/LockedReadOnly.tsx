interface LockedReadOnlyProps {
  planId: string;
}

/**
 * The LOCKED-pre-day-4 pane of the WeekEditor. Group 13b subtask 2 wires the
 * read-only commit list + StateBadge here.
 */
export function LockedReadOnly({ planId: _planId }: LockedReadOnlyProps) {
  return (
    <div data-testid="week-editor-locked-readonly">
      Week is locked. Reconciliation opens Friday.
    </div>
  );
}
