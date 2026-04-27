import { useGetCurrentForMeQuery } from '@wc/rtk-api-client';
import type { WeeklyPlanResponse } from '@wc/rtk-api-client';
import type { FetchBaseQueryError } from '@reduxjs/toolkit/query';
import { getEmployeeTimezone, isReconcileEligible } from '../lib/timezone';

interface WeekEditorProps {
  /**
   * Current instant. Injected so tests can pin the clock and so subtask 10's
   * TZ-aware helper can replace the inline UTC math without changing the
   * call site.
   */
  now: Date;
  /**
   * Employee timezone (IANA). Defaults to the browser's resolved TZ via
   * `getEmployeeTimezone()`; tests inject an explicit value so reconcile-
   * eligibility math is deterministic regardless of CI/local runner clock.
   */
  tz?: string;
}

/**
 * State-aware container for the current-week page. Owns the fetch for
 * `GET /plans/me/current` and routes to one of five child surfaces:
 *
 *   - blank state         : no plan exists (404)
 *   - DRAFT editor        : commit CRUD, links to RCDOPicker / ChessTier
 *   - LOCKED read-only    : weekStart..+4d, week is locked but not reconcile-eligible
 *   - reconcile mode      : LOCKED + now >= weekStart + 4d
 *   - reconciled summary  : RECONCILED, read-only with carry-forward affordances
 *
 * Loading and error states render generic placeholders. The real surfaces
 * inside each mode land in subtasks 3-9.
 */
export function WeekEditor({ now, tz }: WeekEditorProps) {
  const resolvedTz = getEmployeeTimezone(tz);
  const { data, error, isLoading } = useGetCurrentForMeQuery();

  if (isLoading) {
    return <div data-testid="week-editor-loading">Loading…</div>;
  }

  if (error) {
    if (isFetchError(error) && error.status === 404) {
      return <BlankState />;
    }
    return (
      <div data-testid="week-editor-error" role="alert">
        Couldn’t load this week’s plan.
      </div>
    );
  }

  if (!data) {
    // Shouldn't happen -- RTK Query gives us either data, error, or isLoading.
    // Render the error banner rather than crashing if it ever does.
    return (
      <div data-testid="week-editor-error" role="alert">
        Couldn’t load this week’s plan.
      </div>
    );
  }

  return <PlanRouter plan={data} now={now} tz={resolvedTz} />;
}

function PlanRouter({ plan, now, tz }: { plan: WeeklyPlanResponse; now: Date; tz: string }) {
  switch (plan.state) {
    case 'DRAFT':
      return <DraftMode planId={plan.id} />;
    case 'LOCKED':
      return isReconcileEligible(plan.weekStart, now, tz) ? (
        <ReconcileMode planId={plan.id} />
      ) : (
        <LockedReadOnly planId={plan.id} />
      );
    case 'RECONCILED':
      return <ReconciledSummary planId={plan.id} />;
    case 'ARCHIVED':
      // ARCHIVED plans aren't returned by /plans/me/current in normal operation
      // (the current-week query targets the live plan, not historical). Render
      // the read-only summary if it ever surfaces here so the UI degrades safely.
      return <ReconciledSummary planId={plan.id} />;
  }
}

function isFetchError(err: unknown): err is FetchBaseQueryError {
  return typeof err === 'object' && err !== null && 'status' in err;
}

function BlankState() {
  return (
    <div data-testid="week-editor-blank">
      <p>You haven’t started this week yet.</p>
      <button type="button">Create plan</button>
    </div>
  );
}

function DraftMode({ planId: _planId }: { planId: string }) {
  return <div data-testid="week-editor-draft">Draft editor (subtasks 3-4 land here).</div>;
}

function LockedReadOnly({ planId: _planId }: { planId: string }) {
  return (
    <div data-testid="week-editor-locked-readonly">
      Week is locked. Reconciliation opens Friday.
    </div>
  );
}

function ReconcileMode({ planId: _planId }: { planId: string }) {
  return <div data-testid="week-editor-reconcile">Reconciliation (subtasks 5-7 land here).</div>;
}

function ReconciledSummary({ planId: _planId }: { planId: string }) {
  return <div data-testid="week-editor-reconciled">Week reconciled.</div>;
}
