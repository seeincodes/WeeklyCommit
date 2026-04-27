import { useGetCurrentForMeQuery } from '@wc/rtk-api-client';
import type { WeeklyPlanResponse } from '@wc/rtk-api-client';
import type { FetchBaseQueryError } from '@reduxjs/toolkit/query';
import { getEmployeeTimezone, isReconcileEligible } from '../lib/timezone';
import { BlankState } from './modes/BlankState';
import { DraftMode } from './modes/DraftMode';
import { LockedReadOnly } from './modes/LockedReadOnly';
import { ReconcileMode } from './modes/ReconcileMode';
import { ReconciledSummary } from './modes/ReconciledSummary';

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
 * `GET /plans/me/current` and dispatches to one of five mode panes (each a
 * focused component under `./modes/`):
 *
 *   - BlankState           : no plan exists (404)
 *   - DraftMode            : commit CRUD, links to RCDOPicker / ChessTier
 *   - LockedReadOnly       : weekStart..+4d, week is locked but not reconcile-eligible
 *   - ReconcileMode        : LOCKED + now >= weekStart + 4d
 *   - ReconciledSummary    : RECONCILED, read-only with carry-forward affordances
 *
 * Loading and error states render generic placeholders. The mode-specific UX
 * (commit-create form, ReconcileTable, ReflectionField, etc.) lives in each
 * mode's own file -- group 13b wires them in.
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
