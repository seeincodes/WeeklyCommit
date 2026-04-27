import { useEffect, useRef, useState } from 'react';
import {
  useCarryForwardMutation,
  useListCommitsQuery,
  useTransitionMutation,
  useUpdateCommitMutation,
  useUpdateReflectionMutation,
} from '@wc/rtk-api-client';
import { isCarryEligible } from '../../lib/carryEligibility';
import { CarryAllButton, CarryForwardRow } from '../CarryForwardRow';
import { ReconcileTable } from '../ReconcileTable';
import { ReflectionField } from '../ReflectionField';
import { StateBadge } from '../StateBadge';

interface ReconcileModeProps {
  planId: string;
  /**
   * The plan's reflection note, passed through from `<WeekEditor />` so this
   * pane doesn't refetch the plan -- the caller already has it in scope.
   * Optional + nullable because the backend treats reflection as optional and
   * `WeeklyPlanResponse.reflectionNote` is `string | undefined`. Same shape
   * `<ReconciledSummary>` accepts (Task 13b-2 precedent).
   */
  reflectionNote?: string | undefined;
}

/**
 * Debounce window for reflection-note saves. 750ms is short enough that the
 * "Submit reconciliation" button feels live (a final keystroke + click lands
 * the save before the transition fires) and long enough that mid-thought
 * pauses don't burn a network round-trip per character.
 */
const REFLECTION_DEBOUNCE_MS = 750;

/**
 * The LOCKED-post-day-4 reconciliation pane of the WeekEditor. Thin data-
 * fetching shell that wires:
 *   - `<StateBadge state="LOCKED" isReconcileEligible />` for the next-action hint
 *   - `<ReconcileTable>` -> `useUpdateCommitMutation` (per-row `actualStatus`/`actualNote` saves)
 *   - `<ReflectionField>` -> `useUpdateReflectionMutation` (debounced 750ms)
 *   - "Submit reconciliation" -> `useTransitionMutation({to:'RECONCILED'})`
 *   - per-row `<CarryForwardRow>` + bulk `<CarryAllButton>` -> `useCarryForwardMutation`
 *
 * Carry-forward stays available here so the IC can spawn next-week twins for
 * MISSED/PARTIAL work as part of the reconcile flow itself, before the plan
 * transitions to RECONCILED. (`<ReconciledSummary>` keeps the same affordance
 * post-transition per [MVP6].)
 */
export function ReconcileMode({ planId, reflectionNote = '' }: ReconcileModeProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });
  const [updateCommit] = useUpdateCommitMutation();
  const [updateReflection] = useUpdateReflectionMutation();
  const [transition, { isLoading: isSubmitting, error: submitError }] = useTransitionMutation();
  const [carryForward] = useCarryForwardMutation();

  const [reflection, setReflection] = useState(reflectionNote);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    // Skip the no-op "initial-equal" path so we don't fire a save just for
    // mounting with the prop value already in state.
    if (reflection === reflectionNote) return;
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      void updateReflection({ planId, body: { reflectionNote: reflection } });
    }, REFLECTION_DEBOUNCE_MS);
    return () => clearTimeout(debounceRef.current);
  }, [reflection, reflectionNote, planId, updateReflection]);

  if (isLoading) {
    return (
      <div data-testid="reconcile-loading" className="text-sm text-gray-500">
        Loading the week to reconcile…
      </div>
    );
  }
  if (error || !commits) {
    return (
      <div data-testid="reconcile-error" role="alert" className="text-sm text-red-700">
        Couldn’t load commits. Try refreshing the page.
      </div>
    );
  }

  const handleRowUpdate = (
    commitId: string,
    patch: { actualStatus?: 'DONE' | 'PARTIAL' | 'MISSED'; actualNote?: string },
  ) => {
    void updateCommit({ commitId, body: patch });
  };

  const handleSubmit = () => {
    void transition({ planId, body: { to: 'RECONCILED' } });
  };

  return (
    <div data-testid="week-editor-reconcile" className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <StateBadge state="LOCKED" isReconcileEligible={true} />
        <button
          type="button"
          onClick={handleSubmit}
          disabled={isSubmitting}
          className="bg-emerald-600 text-white rounded px-4 py-2 disabled:bg-gray-300"
        >
          {isSubmitting ? 'Submitting…' : 'Submit reconciliation'}
        </button>
      </div>
      {submitError && (
        <div data-testid="reconcile-submit-error" role="alert" className="text-red-700 text-sm">
          Couldn’t submit. Try again.
        </div>
      )}
      <ReconcileTable commits={commits} onUpdate={handleRowUpdate} />
      <ReflectionField value={reflection} onChange={setReflection} />
      <CarryAllButton
        commits={commits}
        onCarryAll={(ids) => {
          for (const id of ids) {
            void carryForward({ commitId: id });
          }
        }}
      />
      <div className="flex flex-col gap-2">
        {commits.map((c) =>
          isCarryEligible(c) ? (
            <CarryForwardRow
              key={`carry-${c.id}`}
              commit={c}
              onCarry={(id) => void carryForward({ commitId: id })}
            />
          ) : null,
        )}
      </div>
    </div>
  );
}
