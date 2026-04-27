import type { SupportingOutcome } from '@wc/rtk-api-client';
import { RCDOPickerContainer } from '../RCDOPickerContainer';

interface DraftModeProps {
  planId: string;
}

/**
 * The DRAFT-state pane of the WeekEditor. Today only renders the
 * RCDOPickerContainer so end-to-end RCDO data flow stays exercised; the full
 * commit-create form + existing-commits list + Lock Week button + StateBadge
 * land in group 13b subtask 1.
 */
export function DraftMode({ planId: _planId }: DraftModeProps) {
  return (
    <div data-testid="week-editor-draft" className="flex flex-col gap-4">
      <RCDOPickerContainer onSelect={handlePickerSelect} />
    </div>
  );
}

function handlePickerSelect(_outcome: SupportingOutcome): void {
  // no-op until commit creation lands; group 13b subtask 1 wires this to
  // useCreateCommitMutation.
}
