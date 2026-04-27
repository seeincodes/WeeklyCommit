# 13b: Mode-Pane Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing tested IC + Manager components into the WeekEditor mode panes and the Team routes so Phase-1 MVP user flows actually flow end-to-end.

**Architecture:** Each WeekEditor mode (`DraftMode`, `LockedReadOnly`, `ReconcileMode`, `ReconciledSummary`) and each Team route (`TeamPage`, `TeamMemberPage`) becomes a thin data-fetching shell that composes the already-built presentational components against the RTK Query hooks shipped in group 10. No new components are created — we wire what exists. Drawer state for `TeamPage` is held in URL search-params so deep-linking and the back button work without a Redux slice.

**Tech Stack:** React 18, TypeScript 5.x strict, Redux Toolkit + RTK Query, react-router-dom 6, Flowbite + Tailwind, Vitest + React Testing Library.

**Discipline (per user decision 2026-04-27):** Hybrid TDD —
- **Full TDD** on Task 3 (`DraftMode`) and Task 4 (`ReconcileMode`) — both invoke state-changing mutations whose drift would be caught only by the state-machine tests we don't have time to add backend-side.
- **Golden-path implementation** on Tasks 1, 2, 5, 6 (`LockedReadOnly`, `ReconciledSummary`, `TeamPage`, `TeamMemberPage`) — read-mostly views; correctness is dominated by hook wiring, which we'll click-test in the running stack.
- **Click-test in browser after every task.** Local Vite + docker-compose stack must stay healthy throughout (we stood it up earlier today; restart commands at the bottom of this plan).
- **Commit after every task.** No batching. Per CLAUDE.md: do not commit while CI is red — but we are not running CI today; we'll run `yarn lint` + `yarn typecheck` + targeted `vitest` per task instead, and let the next CI run after merge surface anything we missed.
- **Co-Authored-By trailer**: per memory `feedback_no_coauthor_trailer.md`, omit it.

**Out of scope for today:**
- Group 14 (infra), Group 15 (acceptance gates), full polish (Phase 2).
- Re-enabling the four `@pending` Cypress feature files. We'll do that in Task 7 if time permits, but it's the first thing to drop if we're behind.
- New unit-test cases for components that already have tests. We're testing *integration* behavior at the mode-pane level only.

---

## File Structure

**Modified files (one per task):**
- Task 1: `apps/weekly-commit-ui/src/components/modes/LockedReadOnly.tsx`
- Task 2: `apps/weekly-commit-ui/src/components/modes/ReconciledSummary.tsx`
- Task 3: `apps/weekly-commit-ui/src/components/modes/DraftMode.tsx` + new `apps/weekly-commit-ui/src/components/modes/DraftMode.test.tsx`
- Task 4: `apps/weekly-commit-ui/src/components/modes/ReconcileMode.tsx` + new `apps/weekly-commit-ui/src/components/modes/ReconcileMode.test.tsx`
- Task 5: `apps/weekly-commit-ui/src/routes/TeamPage.tsx`
- Task 6: `apps/weekly-commit-ui/src/routes/TeamMemberPage.tsx`

**New files:**
- `apps/weekly-commit-ui/src/components/modes/DraftMode.test.tsx` (Task 3)
- `apps/weekly-commit-ui/src/components/modes/ReconcileMode.test.tsx` (Task 4)
- `apps/weekly-commit-ui/src/components/modes/CommitCreateForm.tsx` (Task 3) — extracted because the form has enough internal state to deserve its own file
- `apps/weekly-commit-ui/src/components/modes/CommitCreateForm.test.tsx` (Task 3)

**Touched but not redesigned:**
- `apps/weekly-commit-ui/src/components/WeekEditor.tsx` — already imports the modes from `./modes/`; we don't need to touch this file at all unless integration reveals a missing prop.

**API hooks (all exist in `libs/rtk-api-client/src/api.ts`, no library changes needed):**
- `useGetCurrentForMeQuery()` — already used by WeekEditor
- `useCreateCurrentForMeMutation()` — `BlankState` calls this (already wired today)
- `useListCommitsQuery({planId})` — Tasks 1, 2, 3, 4
- `useCreateCommitMutation()` — Task 3
- `useUpdateCommitMutation()` — Tasks 3, 4
- `useDeleteCommitMutation()` — Task 3
- `useTransitionMutation()` — Tasks 3 (DRAFT→LOCKED), 4 (LOCKED→RECONCILED)
- `useUpdateReflectionMutation()` — Task 4
- `useCarryForwardMutation()` — Tasks 2, 4
- `useGetTeamRollupQuery({managerId, weekStart})` — Task 5
- `useGetPlanByEmployeeAndWeekQuery({employeeId, weekStart})` — Task 6 (already used by `IcDrawer`)

**Conventions agreed once, applied across tasks:**
- **Loading state**: `<div data-testid="<mode>-loading">Loading…</div>` matches the existing `WeekEditor` loading style.
- **Error state**: `<div data-testid="<mode>-error" role="alert">Couldn't load …</div>` matches existing `WeekEditor` error style.
- **Mutation submit button copy**: present-progressive on pending (`"Saving…"`, `"Locking…"`, `"Submitting…"`); plain verb otherwise.
- **Mutation error display**: rely on the global `<ConflictToast />` for 409s (already wired in store); show a per-form red banner for non-409s.
- **`weekStart` source**: derive from the parent's `plan.weekStart` (the WeekEditor already has the plan in scope when it routes to a mode).

---

## Task 1: LockedReadOnly — read-only commit list + StateBadge

**Discipline:** Golden-path implementation. No new test file.

**Files:**
- Modify: `apps/weekly-commit-ui/src/components/modes/LockedReadOnly.tsx` (rewrite)

**Props:** `{ planId: string }` (existing; do not break the WeekEditor call site).

**Behavior:**
- Fetch `useListCommitsQuery({planId})`.
- Loading: render `<div data-testid="locked-readonly-loading">Loading…</div>`.
- Error: render `<div data-testid="locked-readonly-error" role="alert">Couldn't load commits.</div>`.
- Success: render `<StateBadge state="LOCKED" isReconcileEligible={false} />` and `<ChessTier commits={commits} renderCommit={renderRow} />` where `renderRow(commit)` is a presentational read-only row showing title, supportingOutcomeId, and actualStatus pill.
- The "Reconciliation opens Friday" copy already lives in `<StateBadge>` for LOCKED-pre-day-4; do not duplicate it.

- [ ] **Step 1: Rewrite LockedReadOnly.tsx**

```tsx
import { useListCommitsQuery, type WeeklyCommitResponse } from '@wc/rtk-api-client';
import { ChessTier } from '../ChessTier';
import { StateBadge } from '../StateBadge';

interface LockedReadOnlyProps {
  planId: string;
}

export function LockedReadOnly({ planId }: LockedReadOnlyProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });

  if (isLoading) {
    return <div data-testid="locked-readonly-loading">Loading…</div>;
  }
  if (error || !commits) {
    return (
      <div data-testid="locked-readonly-error" role="alert">
        Couldn’t load commits.
      </div>
    );
  }

  return (
    <div data-testid="week-editor-locked-readonly" className="flex flex-col gap-4">
      <StateBadge state="LOCKED" isReconcileEligible={false} />
      <ChessTier commits={commits} renderCommit={renderReadOnlyRow} />
    </div>
  );
}

function renderReadOnlyRow(
  commit: WeeklyCommitResponse,
  _isTopRock: boolean,
): React.ReactNode {
  return (
    <div
      key={commit.id}
      data-testid={`locked-row-${commit.id}`}
      className="flex items-center gap-3 px-3 py-2 border-b last:border-b-0"
    >
      <span className="font-medium text-gray-900">{commit.title}</span>
      <span
        className="ml-auto text-xs uppercase tracking-wide text-gray-500"
        data-testid={`locked-row-${commit.id}-status`}
      >
        {commit.actualStatus}
      </span>
    </div>
  );
}
```

- [ ] **Step 2: Run typecheck and lint**

Run: `yarn workspace @wc/weekly-commit-ui typecheck && yarn workspace @wc/weekly-commit-ui lint --no-cache`
Expected: PASS for both.

- [ ] **Step 3: Click-test in the browser**

In a separate tab, the dev stack is already running (Vite on :4184, docker-compose on :8080). To force a LOCKED plan, open the dev role picker as MANAGER (`http://localhost:4184/?devRole=MANAGER`), then via DevTools console:

```js
// One-time: create a plan if blank-state, then transition it to LOCKED.
// Run only if the page is showing BlankState.
const token = localStorage.__wc_dev_token__ || (document.cookie.match(/devToken=([^;]+)/)?.[1]);
// Easiest: click "Create plan" on BlankState; then in DevTools Network tab grab the planId from the response.
// Then to transition:
fetch('/api/v1/plans/<PLAN_ID>/transitions', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ to: 'LOCKED' }) }).then(r => r.json()).then(console.log);
```

Expected on reload: `data-testid="week-editor-locked-readonly"` is in the DOM, the StateBadge reads "Locked" with the "Reconciliation opens Friday" hint, and the (probably empty) commit list renders without errors.

- [ ] **Step 4: Commit**

```bash
git add apps/weekly-commit-ui/src/components/modes/LockedReadOnly.tsx
git commit -m "task(13b-1): wire LockedReadOnly to commits + StateBadge"
```

---

## Task 2: ReconciledSummary — read-only with carry-forward + carry-streak

**Discipline:** Golden-path implementation. No new test file.

**Files:**
- Modify: `apps/weekly-commit-ui/src/components/modes/ReconciledSummary.tsx` (rewrite)

**Props:** `{ planId: string; reflectionNote?: string }` — accept the reflection from the parent so we don't refetch the plan.

This requires a small WeekEditor change: pass `reflectionNote` through. Do that in this task.

- [ ] **Step 1: Update WeekEditor.tsx to pass reflectionNote**

File: `apps/weekly-commit-ui/src/components/WeekEditor.tsx`

Find the `PlanRouter` switch arms that render `<ReconciledSummary planId={plan.id} />` and change them to:

```tsx
case 'RECONCILED':
  return <ReconciledSummary planId={plan.id} reflectionNote={plan.reflectionNote} />;
case 'ARCHIVED':
  return <ReconciledSummary planId={plan.id} reflectionNote={plan.reflectionNote} />;
```

(`plan.reflectionNote` comes from `WeeklyPlanResponse` — the openapi-typescript generated shape includes it as an optional string.)

- [ ] **Step 2: Rewrite ReconciledSummary.tsx**

```tsx
import {
  useCarryForwardMutation,
  useListCommitsQuery,
  type WeeklyCommitResponse,
} from '@wc/rtk-api-client';
import { CarryAllButton, CarryForwardRow, isCarryEligible } from '../CarryForwardRow';
import { CarryStreakBadge } from '../CarryStreakBadge';
import { ChessTier } from '../ChessTier';
import { StateBadge } from '../StateBadge';

interface ReconciledSummaryProps {
  planId: string;
  reflectionNote?: string;
}

export function ReconciledSummary({ planId, reflectionNote }: ReconciledSummaryProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });
  const [carryForward, { isLoading: isCarrying }] = useCarryForwardMutation();

  if (isLoading) {
    return <div data-testid="reconciled-loading">Loading…</div>;
  }
  if (error || !commits) {
    return (
      <div data-testid="reconciled-error" role="alert">
        Couldn’t load commits.
      </div>
    );
  }

  const eligible = commits.filter(isCarryEligible);

  const renderRow = (commit: WeeklyCommitResponse, _isTopRock: boolean): React.ReactNode => (
    <div
      key={commit.id}
      data-testid={`reconciled-row-${commit.id}`}
      className="flex items-center gap-3 px-3 py-2 border-b last:border-b-0"
    >
      <span className="font-medium text-gray-900">{commit.title}</span>
      <span className="text-xs uppercase tracking-wide text-gray-500">
        {commit.actualStatus}
      </span>
      <CarryStreakBadge streak={commit.carryStreak ?? 0} />
      <span className="ml-auto">
        <CarryForwardRow
          commit={commit}
          onCarry={(id) => carryForward({ commitId: id })}
          disabled={isCarrying}
        />
      </span>
    </div>
  );

  return (
    <div data-testid="week-editor-reconciled" className="flex flex-col gap-4">
      <StateBadge state="RECONCILED" isReconcileEligible={false} />
      {reflectionNote != null && reflectionNote !== '' && (
        <section
          data-testid="reconciled-reflection"
          className="rounded border border-gray-200 bg-gray-50 p-3"
        >
          <h3 className="text-sm font-semibold text-gray-700 mb-1">Reflection</h3>
          <p className="whitespace-pre-wrap text-gray-900">{reflectionNote}</p>
        </section>
      )}
      <CarryAllButton
        commits={eligible}
        onCarryAll={(ids) => Promise.all(ids.map((id) => carryForward({ commitId: id })))}
        disabled={isCarrying}
      />
      <ChessTier commits={commits} renderCommit={renderRow} />
    </div>
  );
}
```

**Note on the API surface:** the existing `<CarryForwardRow>` and `<CarryAllButton>` props are shown as the test files describe them. If the real prop names differ (e.g. `onCarryForward` instead of `onCarry`), adjust to match — typecheck will catch it. Do not invent API; read the component source if the field name is unclear.

- [ ] **Step 3: Run typecheck and lint**

Run: `yarn workspace @wc/weekly-commit-ui typecheck && yarn workspace @wc/weekly-commit-ui lint --no-cache`
Expected: PASS.

- [ ] **Step 4: Click-test in browser**

To force a RECONCILED plan: from a LOCKED plan past day-4 reconcile-eligibility (or via direct API), `POST /plans/<id>/transitions { to: 'RECONCILED' }`. Verify:
- `data-testid="week-editor-reconciled"` renders
- StateBadge shows RECONCILED
- Reflection block renders if non-empty
- CarryForward affordances visible on MISSED/PARTIAL rows

- [ ] **Step 5: Commit**

```bash
git add apps/weekly-commit-ui/src/components/modes/ReconciledSummary.tsx apps/weekly-commit-ui/src/components/WeekEditor.tsx
git commit -m "task(13b-2): wire ReconciledSummary read-only + carry-forward"
```

---

## Task 3: DraftMode — commit-create form + commit list + Lock Week

**Discipline:** Full TDD. The commit-create flow is the hottest path in the app and uses three mutations whose state-machine drift would be brutal to debug at demo time.

**Files:**
- Create: `apps/weekly-commit-ui/src/components/modes/CommitCreateForm.tsx`
- Create: `apps/weekly-commit-ui/src/components/modes/CommitCreateForm.test.tsx`
- Create: `apps/weekly-commit-ui/src/components/modes/DraftMode.test.tsx`
- Modify: `apps/weekly-commit-ui/src/components/modes/DraftMode.tsx` (rewrite)

This task is the largest. Break into sub-blocks.

### 3A. CommitCreateForm — extracted form component

The form has internal state (title, selected outcome, tier, hours, tags) that warrants its own component. It accepts an `onSubmit` callback so DraftMode owns the actual mutation call.

- [ ] **Step 1: Write the failing test for CommitCreateForm**

File: `apps/weekly-commit-ui/src/components/modes/CommitCreateForm.test.tsx`

```tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CommitCreateForm } from './CommitCreateForm';

const noopOutcome = {
  id: 'so-1',
  title: 'Hit revenue target',
  breadcrumb: { levels: [] },
};

describe('<CommitCreateForm />', () => {
  it('disables submit until title and supporting outcome are set', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<CommitCreateForm onSubmit={onSubmit} disabled={false} />);

    expect(screen.getByRole('button', { name: /add commit/i })).toBeDisabled();

    await user.type(screen.getByLabelText(/title/i), 'Ship the picker integration');
    expect(screen.getByRole('button', { name: /add commit/i })).toBeDisabled();

    // Simulate the picker selecting an outcome.
    // The form exposes an imperative-ish `data-testid="form-outcome-fixture-select"`
    // button in the test build only. (Alternative: refactor RCDOPickerContainer to
    // accept a controlled `value` + `onChange` prop. We keep the button approach
    // here because it's a one-liner in the test render.)
    // For this test, render with a `__testOutcome={noopOutcome}` prop that pre-
    // selects the outcome.
  });

  it('emits the commit payload on submit', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <CommitCreateForm onSubmit={onSubmit} disabled={false} __testOutcome={noopOutcome} />,
    );

    await user.type(screen.getByLabelText(/title/i), 'Ship the picker integration');
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Ship the picker integration',
      supportingOutcomeId: 'so-1',
      chessTier: 'PEBBLE',
      estimatedHours: undefined,
      tags: [],
    });
  });

  it('clears the form after a successful submit', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <CommitCreateForm onSubmit={onSubmit} disabled={false} __testOutcome={noopOutcome} />,
    );

    await user.type(screen.getByLabelText(/title/i), 'Ship the picker integration');
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    expect(screen.getByLabelText(/title/i)).toHaveValue('');
  });
});
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `yarn workspace @wc/weekly-commit-ui exec vitest run src/components/modes/CommitCreateForm.test.tsx`
Expected: FAIL with "Cannot find module './CommitCreateForm'".

- [ ] **Step 3: Implement CommitCreateForm.tsx**

File: `apps/weekly-commit-ui/src/components/modes/CommitCreateForm.tsx`

```tsx
import { useState } from 'react';
import type { CreateCommitRequest, SupportingOutcome } from '@wc/rtk-api-client';
import { RCDOPickerContainer } from '../RCDOPickerContainer';

interface CommitCreateFormProps {
  onSubmit: (payload: CreateCommitRequest) => Promise<void> | void;
  disabled?: boolean;
  /** Pre-selects an outcome -- test-only escape hatch. */
  __testOutcome?: SupportingOutcome;
}

const TIERS = ['ROCK', 'PEBBLE', 'SAND'] as const;
type Tier = (typeof TIERS)[number];

export function CommitCreateForm({
  onSubmit,
  disabled = false,
  __testOutcome,
}: CommitCreateFormProps) {
  const [title, setTitle] = useState('');
  const [outcome, setOutcome] = useState<SupportingOutcome | undefined>(__testOutcome);
  const [tier, setTier] = useState<Tier>('PEBBLE');
  const [estimatedHoursRaw, setEstimatedHoursRaw] = useState('');

  const submitDisabled = disabled || title.trim() === '' || outcome == null;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (submitDisabled || outcome == null) return;
    const estimatedHours =
      estimatedHoursRaw === '' ? undefined : Number(estimatedHoursRaw);
    await onSubmit({
      title: title.trim(),
      supportingOutcomeId: outcome.id,
      chessTier: tier,
      estimatedHours,
      tags: [],
    });
    setTitle('');
    setOutcome(__testOutcome);
    setTier('PEBBLE');
    setEstimatedHoursRaw('');
  }

  return (
    <form
      data-testid="commit-create-form"
      onSubmit={handleSubmit}
      className="flex flex-col gap-3 p-4 border border-gray-200 rounded"
    >
      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-gray-700">Title</span>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
          className="border border-gray-300 rounded px-2 py-1"
        />
      </label>
      <RCDOPickerContainer onSelect={setOutcome} />
      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-gray-700">Tier</span>
        <select
          value={tier}
          onChange={(e) => setTier(e.target.value as Tier)}
          className="border border-gray-300 rounded px-2 py-1"
        >
          {TIERS.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-gray-700">Estimated hours (optional)</span>
        <input
          type="number"
          min={0}
          step={0.5}
          value={estimatedHoursRaw}
          onChange={(e) => setEstimatedHoursRaw(e.target.value)}
          className="border border-gray-300 rounded px-2 py-1"
        />
      </label>
      <button
        type="submit"
        disabled={submitDisabled}
        className="bg-blue-600 text-white rounded px-4 py-2 disabled:bg-gray-300"
      >
        Add commit
      </button>
    </form>
  );
}
```

- [ ] **Step 4: Run the test to confirm green**

Run: `yarn workspace @wc/weekly-commit-ui exec vitest run src/components/modes/CommitCreateForm.test.tsx`
Expected: 3 tests PASS.

If a test fails on the picker-selection path because `RCDOPickerContainer` does its own RTK Query call without a Provider, the test render needs a wrapper. **If you hit this, change the test to import `renderWithProviders` from `src/setupTests.ts` (existing helper) — do not invent a new helper.**

### 3B. DraftMode — wires the form + commit list + Lock button

- [ ] **Step 5: Write the failing test for DraftMode**

File: `apps/weekly-commit-ui/src/components/modes/DraftMode.test.tsx`

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query/react';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import { DraftMode } from './DraftMode';
import { server } from '../../../tests/msw/server'; // existing MSW setup
import { http, HttpResponse } from 'msw';

const PLAN_ID = '99999999-9999-9999-9999-999999999999';

function renderWithStore(ui: React.ReactNode) {
  const store = configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  setupListeners(store.dispatch);
  return render(<Provider store={store}>{ui}</Provider>);
}

describe('<DraftMode />', () => {
  beforeEach(() => {
    server.use(
      http.get('http://localhost/api/v1/plans/:id/commits', () =>
        HttpResponse.json({ data: [], meta: {} }),
      ),
    );
  });

  it('renders the commit-create form and an empty list', async () => {
    renderWithStore(<DraftMode planId={PLAN_ID} />);
    await waitFor(() => expect(screen.getByTestId('commit-create-form')).toBeInTheDocument());
    expect(screen.queryAllByTestId(/^draft-row-/)).toHaveLength(0);
  });

  it('POSTs a new commit and refetches on submit', async () => {
    const created = vi.fn();
    server.use(
      http.post(`http://localhost/api/v1/plans/${PLAN_ID}/commits`, async ({ request }) => {
        created(await request.json());
        return HttpResponse.json({
          data: {
            id: 'c1',
            planId: PLAN_ID,
            title: 'Ship picker',
            supportingOutcomeId: 'so-1',
            chessTier: 'PEBBLE',
            displayOrder: 1,
            actualStatus: 'PENDING',
          },
          meta: {},
        });
      }),
    );
    const user = userEvent.setup();
    renderWithStore(<DraftMode planId={PLAN_ID} __testOutcomeId="so-1" />);

    await user.type(screen.getByLabelText(/title/i), 'Ship picker');
    await user.click(screen.getByRole('button', { name: /add commit/i }));

    await waitFor(() => expect(created).toHaveBeenCalledTimes(1));
    expect(created.mock.calls[0][0]).toMatchObject({
      title: 'Ship picker',
      supportingOutcomeId: 'so-1',
      chessTier: 'PEBBLE',
    });
  });

  it('transitions DRAFT → LOCKED on Lock Week click', async () => {
    const transitioned = vi.fn();
    server.use(
      http.post(`http://localhost/api/v1/plans/${PLAN_ID}/transitions`, async ({ request }) => {
        transitioned(await request.json());
        return HttpResponse.json({
          data: {
            id: PLAN_ID,
            employeeId: 'e1',
            weekStart: '2026-04-27',
            state: 'LOCKED',
            version: 2,
          },
          meta: {},
        });
      }),
    );
    const user = userEvent.setup();
    renderWithStore(<DraftMode planId={PLAN_ID} />);

    await user.click(screen.getByRole('button', { name: /lock week/i }));

    await waitFor(() => expect(transitioned).toHaveBeenCalledTimes(1));
    expect(transitioned.mock.calls[0][0]).toEqual({ to: 'LOCKED' });
  });
});
```

- [ ] **Step 6: Run DraftMode test, confirm fail**

Run: `yarn workspace @wc/weekly-commit-ui exec vitest run src/components/modes/DraftMode.test.tsx`
Expected: FAIL — current DraftMode.tsx renders only the picker container, not the form/list/lock button.

- [ ] **Step 7: Rewrite DraftMode.tsx**

```tsx
import {
  useCreateCommitMutation,
  useDeleteCommitMutation,
  useListCommitsQuery,
  useTransitionMutation,
  useUpdateCommitMutation,
  type CreateCommitRequest,
  type WeeklyCommitResponse,
} from '@wc/rtk-api-client';
import { ChessTier } from '../ChessTier';
import { StateBadge } from '../StateBadge';
import { CommitCreateForm } from './CommitCreateForm';

interface DraftModeProps {
  planId: string;
  /** Test escape-hatch -- pre-selects an RCDO outcome in the form. */
  __testOutcomeId?: string;
}

export function DraftMode({ planId, __testOutcomeId }: DraftModeProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });
  const [createCommit, { isLoading: isCreating, error: createError }] =
    useCreateCommitMutation();
  const [deleteCommit] = useDeleteCommitMutation();
  const [updateCommit] = useUpdateCommitMutation();
  const [transition, { isLoading: isLocking, error: lockError }] = useTransitionMutation();

  if (isLoading) {
    return <div data-testid="draft-loading">Loading…</div>;
  }
  if (error) {
    return (
      <div data-testid="draft-error" role="alert">
        Couldn’t load commits.
      </div>
    );
  }

  const safeCommits = commits ?? [];

  const handleCreate = async (payload: CreateCommitRequest) => {
    await createCommit({ planId, body: payload }).unwrap();
  };

  const handleLock = () => {
    void transition({ planId, body: { to: 'LOCKED' } });
  };

  const renderRow = (commit: WeeklyCommitResponse, _isTopRock: boolean): React.ReactNode => (
    <div
      key={commit.id}
      data-testid={`draft-row-${commit.id}`}
      className="flex items-center gap-2 px-3 py-2 border-b last:border-b-0"
    >
      <span className="font-medium text-gray-900">{commit.title}</span>
      <span className="ml-auto flex gap-2">
        <button
          type="button"
          className="text-xs text-blue-600 hover:underline"
          onClick={() =>
            void updateCommit({
              commitId: commit.id,
              body: { title: commit.title }, // placeholder; full edit UI is Phase-2 polish
            })
          }
        >
          Edit
        </button>
        <button
          type="button"
          className="text-xs text-red-600 hover:underline"
          onClick={() => void deleteCommit({ commitId: commit.id })}
        >
          Delete
        </button>
      </span>
    </div>
  );

  return (
    <div data-testid="week-editor-draft" className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <StateBadge state="DRAFT" isReconcileEligible={false} />
        <button
          type="button"
          onClick={handleLock}
          disabled={isLocking}
          className="bg-amber-600 text-white rounded px-4 py-2 disabled:bg-gray-300"
        >
          {isLocking ? 'Locking…' : 'Lock Week'}
        </button>
      </div>
      {(createError || lockError) && (
        <div data-testid="draft-mutation-error" role="alert" className="text-red-700 text-sm">
          Something went wrong. Try again.
        </div>
      )}
      <CommitCreateForm
        onSubmit={handleCreate}
        disabled={isCreating}
        __testOutcome={
          __testOutcomeId
            ? { id: __testOutcomeId, title: 'TEST', breadcrumb: { levels: [] } }
            : undefined
        }
      />
      <ChessTier commits={safeCommits} renderCommit={renderRow} />
    </div>
  );
}
```

- [ ] **Step 8: Run all DraftMode + CommitCreateForm tests**

Run: `yarn workspace @wc/weekly-commit-ui exec vitest run src/components/modes/`
Expected: all PASS.

- [ ] **Step 9: Run typecheck and lint**

Run: `yarn workspace @wc/weekly-commit-ui typecheck && yarn workspace @wc/weekly-commit-ui lint --no-cache`
Expected: PASS.

- [ ] **Step 10: Click-test in browser**

Open http://localhost:4184/ as MANAGER. If BlankState, click "Create plan". Expected on the resulting DRAFT view:
- StateBadge shows DRAFT
- Lock Week button is visible
- Commit-create form renders with title, picker, tier, hours, submit button
- Picker dropdown (from RCDOPickerContainer) shows real RCDO outcomes from the proxy
- Type a title, pick an outcome, submit → new commit appears in the ChessTier list
- Click "Lock Week" → page should rerender to LockedReadOnly (Task 1's surface)

- [ ] **Step 11: Commit**

```bash
git add apps/weekly-commit-ui/src/components/modes/{DraftMode,CommitCreateForm}.tsx apps/weekly-commit-ui/src/components/modes/{DraftMode,CommitCreateForm}.test.tsx
git commit -m "task(13b-3): wire DraftMode commit-create + Lock Week"
```

---

## Task 4: ReconcileMode — ReconcileTable + ReflectionField + Submit + carry-forward

**Discipline:** Full TDD. State-changing path with debounce; tests catch debounce drift cheaply.

**Files:**
- Create: `apps/weekly-commit-ui/src/components/modes/ReconcileMode.test.tsx`
- Modify: `apps/weekly-commit-ui/src/components/modes/ReconcileMode.tsx` (rewrite)

**Reflection debounce:** 750ms — long enough to feel like the user finished a thought, short enough that "Submit" feels live.

- [ ] **Step 1: Write the failing test**

File: `apps/weekly-commit-ui/src/components/modes/ReconcileMode.test.tsx`

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query/react';
import { api, conflictToastSlice } from '@wc/rtk-api-client';
import { ReconcileMode } from './ReconcileMode';
import { server } from '../../../tests/msw/server';
import { http, HttpResponse } from 'msw';

const PLAN_ID = '99999999-9999-9999-9999-999999999999';

function renderWithStore(ui: React.ReactNode) {
  const store = configureStore({
    reducer: {
      [api.reducerPath]: api.reducer,
      conflictToast: conflictToastSlice.reducer,
    },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  setupListeners(store.dispatch);
  return render(<Provider store={store}>{ui}</Provider>);
}

describe('<ReconcileMode />', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    server.use(
      http.get('http://localhost/api/v1/plans/:id/commits', () =>
        HttpResponse.json({
          data: [
            {
              id: 'c1',
              planId: PLAN_ID,
              title: 'Ship picker',
              supportingOutcomeId: 'so-1',
              chessTier: 'PEBBLE',
              displayOrder: 1,
              actualStatus: 'PENDING',
            },
          ],
          meta: {},
        }),
      ),
    );
  });

  afterEach(() => vi.useRealTimers());

  it('PATCHes a commit row when actualStatus changes', async () => {
    const patched = vi.fn();
    server.use(
      http.patch('http://localhost/api/v1/commits/c1', async ({ request }) => {
        patched(await request.json());
        return HttpResponse.json({
          data: { id: 'c1', planId: PLAN_ID, actualStatus: 'DONE' },
          meta: {},
        });
      }),
    );
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    const doneRadio = await screen.findByRole('radio', { name: /done/i });
    await user.click(doneRadio);

    await waitFor(() => expect(patched).toHaveBeenCalledTimes(1));
    expect(patched.mock.calls[0][0]).toMatchObject({ actualStatus: 'DONE' });
  });

  it('debounces reflection PATCH at 750ms', async () => {
    const patched = vi.fn();
    server.use(
      http.patch(`http://localhost/api/v1/plans/${PLAN_ID}`, async ({ request }) => {
        patched(await request.json());
        return HttpResponse.json({
          data: { id: PLAN_ID, employeeId: 'e1', weekStart: '2026-04-27', state: 'LOCKED', version: 3 },
          meta: {},
        });
      }),
    );
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    const textarea = await screen.findByRole('textbox', { name: /reflection/i });
    await user.type(textarea, 'Met goals.');

    // Before debounce window → no fire.
    expect(patched).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(800));
    await waitFor(() => expect(patched).toHaveBeenCalledTimes(1));
    expect(patched.mock.calls[0][0]).toMatchObject({ reflectionNote: 'Met goals.' });
  });

  it('submits the LOCKED → RECONCILED transition', async () => {
    const transitioned = vi.fn();
    server.use(
      http.post(`http://localhost/api/v1/plans/${PLAN_ID}/transitions`, async ({ request }) => {
        transitioned(await request.json());
        return HttpResponse.json({
          data: { id: PLAN_ID, employeeId: 'e1', weekStart: '2026-04-27', state: 'RECONCILED', version: 4 },
          meta: {},
        });
      }),
    );
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWithStore(<ReconcileMode planId={PLAN_ID} reflectionNote="" />);

    await user.click(await screen.findByRole('button', { name: /submit reconciliation/i }));

    await waitFor(() => expect(transitioned).toHaveBeenCalledTimes(1));
    expect(transitioned.mock.calls[0][0]).toEqual({ to: 'RECONCILED' });
  });
});
```

- [ ] **Step 2: Run, confirm failures**

Run: `yarn workspace @wc/weekly-commit-ui exec vitest run src/components/modes/ReconcileMode.test.tsx`
Expected: FAIL — current ReconcileMode is a stub.

- [ ] **Step 3: Rewrite ReconcileMode.tsx**

```tsx
import { useEffect, useRef, useState } from 'react';
import {
  useCarryForwardMutation,
  useListCommitsQuery,
  useTransitionMutation,
  useUpdateCommitMutation,
  useUpdateReflectionMutation,
  type WeeklyCommitResponse,
} from '@wc/rtk-api-client';
import { CarryAllButton, CarryForwardRow, isCarryEligible } from '../CarryForwardRow';
import { ReconcileTable } from '../ReconcileTable';
import { ReflectionField } from '../ReflectionField';
import { StateBadge } from '../StateBadge';

interface ReconcileModeProps {
  planId: string;
  reflectionNote?: string;
}

const DEBOUNCE_MS = 750;

export function ReconcileMode({ planId, reflectionNote = '' }: ReconcileModeProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });
  const [updateCommit] = useUpdateCommitMutation();
  const [updateReflection] = useUpdateReflectionMutation();
  const [transition, { isLoading: isSubmitting, error: submitError }] = useTransitionMutation();
  const [carryForward, { isLoading: isCarrying }] = useCarryForwardMutation();

  const [reflection, setReflection] = useState(reflectionNote);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    if (reflection === reflectionNote) return;
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      void updateReflection({ planId, body: { reflectionNote: reflection } });
    }, DEBOUNCE_MS);
    return () => clearTimeout(debounceRef.current);
  }, [reflection, reflectionNote, planId, updateReflection]);

  if (isLoading) {
    return <div data-testid="reconcile-loading">Loading…</div>;
  }
  if (error) {
    return (
      <div data-testid="reconcile-error" role="alert">
        Couldn’t load commits.
      </div>
    );
  }

  const safeCommits = commits ?? [];
  const eligible = safeCommits.filter(isCarryEligible);

  const handleRowUpdate = (
    commitId: string,
    patch: { actualStatus?: WeeklyCommitResponse['actualStatus']; actualNote?: string },
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
      <ReconcileTable commits={safeCommits} onUpdate={handleRowUpdate} />
      <ReflectionField value={reflection} onChange={setReflection} />
      <CarryAllButton
        commits={eligible}
        onCarryAll={(ids) => Promise.all(ids.map((id) => carryForward({ commitId: id })))}
        disabled={isCarrying}
      />
      {safeCommits.map((c) =>
        isCarryEligible(c) ? (
          <CarryForwardRow
            key={`carry-${c.id}`}
            commit={c}
            onCarry={(id) => carryForward({ commitId: id })}
            disabled={isCarrying}
          />
        ) : null,
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run all reconcile tests, confirm green**

Run: `yarn workspace @wc/weekly-commit-ui exec vitest run src/components/modes/ReconcileMode.test.tsx`
Expected: 3 tests PASS.

- [ ] **Step 5: Update WeekEditor.tsx to pass reflectionNote to ReconcileMode**

Same pattern as Task 2. Change:

```tsx
<ReconcileMode planId={plan.id} />
```
to
```tsx
<ReconcileMode planId={plan.id} reflectionNote={plan.reflectionNote} />
```

- [ ] **Step 6: Run typecheck and lint**

Run: `yarn workspace @wc/weekly-commit-ui typecheck && yarn workspace @wc/weekly-commit-ui lint --no-cache`
Expected: PASS.

- [ ] **Step 7: Click-test in browser**

Force a LOCKED + reconcile-eligible plan (LOCKED state + week-start at least 4 days ago in your TZ — easiest is to insert a row directly with `weekStart='2026-04-21'`, or transition a fresh plan to LOCKED and adjust your system clock if you trust your DST helper). Verify:
- ReconcileTable renders with rows for each commit
- ReflectionField is editable; typing pauses then fires PATCH
- Submit button transitions to RECONCILED → ReconciledSummary appears

- [ ] **Step 8: Commit**

```bash
git add apps/weekly-commit-ui/src/components/modes/ReconcileMode.tsx apps/weekly-commit-ui/src/components/modes/ReconcileMode.test.tsx apps/weekly-commit-ui/src/components/WeekEditor.tsx
git commit -m "task(13b-4): wire ReconcileMode table+reflection+submit+carry"
```

---

## Task 5: TeamPage — TeamRollup + drawer-state via URL

**Discipline:** Golden-path implementation.

**Files:**
- Modify: `apps/weekly-commit-ui/src/routes/TeamPage.tsx` (rewrite)

**Drawer state:** URL search-param `?employeeId=…`. When set, render `<IcDrawer/>` over the rollup.

**managerId:** read from the JWT via the existing principal hook. **If no such hook exists yet** (we haven't grep'd), use a hardcoded const matching the dev-shim MANAGER's `sub` for today's MVP:

```ts
const DEV_MANAGER_ID = '22222222-2222-2222-2222-222222222222';
```

with a `TODO(group-?-auth-context)` comment. This is a deliberate tech-debt take per the same-day plan.

**weekStart:** today, normalized to the Monday-start week, in the employee's TZ. The `getEmployeeTimezone()` helper exists; the Monday-start helper does *not* — for today, derive inline:

```ts
import { getEmployeeTimezone } from '../lib/timezone';

function currentWeekStart(): string {
  const tz = getEmployeeTimezone();
  const now = new Date();
  const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' });
  const parts = fmt.formatToParts(now);
  const yyyy = parts.find((p) => p.type === 'year')!.value;
  const mm = parts.find((p) => p.type === 'month')!.value;
  const dd = parts.find((p) => p.type === 'day')!.value;
  const weekday = parts.find((p) => p.type === 'weekday')!.value; // Mon..Sun
  // ISO weeks start Monday; subtract days until we hit Monday.
  const offsetByWeekday: Record<string, number> = { Mon: 0, Tue: 1, Wed: 2, Thu: 3, Fri: 4, Sat: 5, Sun: 6 };
  const offset = offsetByWeekday[weekday] ?? 0;
  const d = new Date(`${yyyy}-${mm}-${dd}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - offset);
  return d.toISOString().slice(0, 10);
}
```

This is duplicated from the backend's week-start derivation. **TODO(group-?-week-helper)** to consolidate. Acceptable today.

- [ ] **Step 1: Rewrite TeamPage.tsx**

```tsx
import { useSearchParams } from 'react-router-dom';
import { Card } from 'flowbite-react';
import { useGetTeamRollupQuery } from '@wc/rtk-api-client';
import { TeamRollup } from '../components/TeamRollup';
import { MemberCard } from '../components/MemberCard';
import { IcDrawer } from '../components/IcDrawer';
import { getEmployeeTimezone } from '../lib/timezone';

// TODO(group-?-auth-context): replace with JWT-derived managerId via React context.
const DEV_MANAGER_ID = '22222222-2222-2222-2222-222222222222';

function currentWeekStart(): string {
  const tz = getEmployeeTimezone();
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: tz,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  });
  const parts = fmt.formatToParts(new Date());
  const yyyy = parts.find((p) => p.type === 'year')!.value;
  const mm = parts.find((p) => p.type === 'month')!.value;
  const dd = parts.find((p) => p.type === 'day')!.value;
  const weekday = parts.find((p) => p.type === 'weekday')!.value;
  const offsetByWeekday: Record<string, number> = {
    Mon: 0,
    Tue: 1,
    Wed: 2,
    Thu: 3,
    Fri: 4,
    Sat: 5,
    Sun: 6,
  };
  const offset = offsetByWeekday[weekday] ?? 0;
  const d = new Date(`${yyyy}-${mm}-${dd}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - offset);
  return d.toISOString().slice(0, 10);
}

export function TeamPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const drawerEmployeeId = searchParams.get('employeeId') ?? undefined;
  const weekStart = currentWeekStart();

  const { data: rollup, isLoading, error } = useGetTeamRollupQuery({
    managerId: DEV_MANAGER_ID,
    weekStart,
  });

  const openDrawer = (employeeId: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('employeeId', employeeId);
    setSearchParams(next);
  };

  const closeDrawer = () => {
    const next = new URLSearchParams(searchParams);
    next.delete('employeeId');
    setSearchParams(next);
  };

  return (
    <div data-testid="team-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-5xl">
        <h1 className="text-2xl font-bold text-gray-900">Team rollup</h1>
        {isLoading && <div data-testid="team-loading">Loading…</div>}
        {error && (
          <div data-testid="team-error" role="alert">
            Couldn’t load team rollup.
          </div>
        )}
        {rollup && (
          <TeamRollup
            rollup={rollup}
            renderMember={(m) => (
              <MemberCard key={m.employeeId} member={m} onClick={openDrawer} />
            )}
          />
        )}
      </Card>
      {drawerEmployeeId && (
        <IcDrawer
          employeeId={drawerEmployeeId}
          weekStart={weekStart}
          onClose={closeDrawer}
        />
      )}
    </div>
  );
}
```

**Note:** the `<TeamRollup>`, `<MemberCard>`, `<IcDrawer>` prop shapes should match the existing component signatures. Read the source if any field name doesn't typecheck — do not invent.

- [ ] **Step 2: Run typecheck and lint**

Run: `yarn workspace @wc/weekly-commit-ui typecheck && yarn workspace @wc/weekly-commit-ui lint --no-cache`
Expected: PASS.

- [ ] **Step 3: Click-test**

Open http://localhost:4184/#/weekly-commit/team as MANAGER. Expected: rollup loads (likely empty since no IC plans exist yet — that's fine). Click any member card → URL gets `?employeeId=…` → drawer opens. Click outside drawer → URL clears → drawer closes. Use back button → drawer toggles in step.

- [ ] **Step 4: Commit**

```bash
git add apps/weekly-commit-ui/src/routes/TeamPage.tsx
git commit -m "task(13b-5): wire TeamPage rollup + drawer URL state"
```

---

## Task 6: TeamMemberPage — addressable single-member drawer

**Discipline:** Golden-path implementation.

**Files:**
- Modify: `apps/weekly-commit-ui/src/routes/TeamMemberPage.tsx` (rewrite)

This is the deep-link target. Same `IcDrawer` as Task 5 but the close action navigates back to `/team`.

- [ ] **Step 1: Rewrite TeamMemberPage.tsx**

```tsx
import { useNavigate, useParams } from 'react-router-dom';
import { Card } from 'flowbite-react';
import { IcDrawer } from '../components/IcDrawer';
import { getEmployeeTimezone } from '../lib/timezone';

function currentWeekStart(): string {
  const tz = getEmployeeTimezone();
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: tz,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  });
  const parts = fmt.formatToParts(new Date());
  const yyyy = parts.find((p) => p.type === 'year')!.value;
  const mm = parts.find((p) => p.type === 'month')!.value;
  const dd = parts.find((p) => p.type === 'day')!.value;
  const weekday = parts.find((p) => p.type === 'weekday')!.value;
  const offsetByWeekday: Record<string, number> = {
    Mon: 0,
    Tue: 1,
    Wed: 2,
    Thu: 3,
    Fri: 4,
    Sat: 5,
    Sun: 6,
  };
  const offset = offsetByWeekday[weekday] ?? 0;
  const d = new Date(`${yyyy}-${mm}-${dd}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - offset);
  return d.toISOString().slice(0, 10);
}

export function TeamMemberPage() {
  const { employeeId } = useParams<{ employeeId: string }>();
  const navigate = useNavigate();

  if (!employeeId) {
    return (
      <div data-testid="team-member-page" className="p-6 bg-gray-50 min-h-screen">
        <Card className="max-w-3xl">
          <h1 className="text-2xl font-bold text-gray-900">Team member</h1>
          <p data-testid="team-member-missing" role="alert">
            No employee id in the URL.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div data-testid="team-member-page" className="p-6 bg-gray-50 min-h-screen">
      <Card className="max-w-3xl">
        <h1 className="text-2xl font-bold text-gray-900">Team member</h1>
        <p className="text-gray-600" data-testid="team-member-id">
          Viewing employee: {employeeId}
        </p>
      </Card>
      <IcDrawer
        employeeId={employeeId}
        weekStart={currentWeekStart()}
        onClose={() => navigate('/weekly-commit/team')}
      />
    </div>
  );
}
```

**Duplication note:** `currentWeekStart` is duplicated between Task 5 and Task 6. Acceptable today; consolidation is a Phase-2 cleanup.

- [ ] **Step 2: Run typecheck and lint**

Run: `yarn workspace @wc/weekly-commit-ui typecheck && yarn workspace @wc/weekly-commit-ui lint --no-cache`
Expected: PASS.

- [ ] **Step 3: Click-test**

Navigate directly to http://localhost:4184/#/weekly-commit/team/22222222-2222-2222-2222-222222222222 (any employee UUID — it'll either show a real plan or an empty drawer). Verify drawer renders, close → back to `/team`.

- [ ] **Step 4: Commit**

```bash
git add apps/weekly-commit-ui/src/routes/TeamMemberPage.tsx
git commit -m "task(13b-6): wire TeamMemberPage IcDrawer + close-to-team"
```

---

## Task 7 (stretch): Re-enable Cypress @pending features

**Discipline:** Smoke-only. Drop if behind schedule.

**Files:**
- Modify: `apps/weekly-commit-ui/cypress/e2e/{commit-entry,lock-week,reconcile,manager-review}.feature`

Strip `@pending` tags. Run locally: `CYPRESS_TAGS='' yarn workspace @wc/weekly-commit-ui test:cypress`. If failures land, **do not fix in this plan** — file as a follow-up task. The point of Task 7 is to confirm the Cypress shape *can* exercise these flows now; failure here is a green light to investigate, not a blocker for the day's MVP.

- [ ] **Step 1: Drop @pending from each .feature**

```bash
sed -i '' 's/@pending //g' apps/weekly-commit-ui/cypress/e2e/{commit-entry,lock-week,reconcile,manager-review}.feature
```

- [ ] **Step 2: Run Cypress locally**

```bash
CYPRESS_TAGS='' yarn workspace @wc/weekly-commit-ui test:cypress
```

- [ ] **Step 3: Triage**

If green → commit. If red → revert the sed, file the failures as a follow-up subtask in TASK_LIST.md, do not fix today.

- [ ] **Step 4: Commit (if green)**

```bash
git add apps/weekly-commit-ui/cypress/e2e/
git commit -m "task(13b-7): re-enable cross-remote E2E .features after wiring"
```

---

## Cross-Cutting: TASK_LIST checkboxes

After each commit (Tasks 1-6), tick the corresponding `- [ ]` in [docs/TASK_LIST.md](../../TASK_LIST.md) Group 13b. Do this in the same commit that lands the work, not a separate one — keeps the audit trail clean.

---

## Local stack restart commands (reference)

```bash
# If stack is down:
docker compose -f apps/weekly-commit-ui/docker-compose.e2e.yml up -d
yarn workspace @wc/weekly-commit-ui dev > /tmp/wc-vite.log 2>&1 &
disown

# Health check:
curl -s http://localhost:8080/actuator/health/readiness   # {"status":"UP"}
curl -sI http://localhost:4184/                            # 200

# Tear down:
kill $(lsof -ti:4184)
docker compose -f apps/weekly-commit-ui/docker-compose.e2e.yml down -v
```

---

## Self-Review Notes

**Spec coverage:**
- Group 13b subtask 1 (DraftMode) → Task 3 ✓
- Group 13b subtask 2 (LockedReadOnly) → Task 1 ✓
- Group 13b subtask 3 (ReconcileMode) → Task 4 ✓
- Group 13b subtask 4 (ReconciledSummary) → Task 2 ✓
- Group 13b subtask 5 (TeamPage) → Task 5 ✓
- Group 13b subtask 6 (TeamMemberPage) → Task 6 ✓
- Group 13b subtask 7 (re-enable .features) → Task 7 (stretch) ✓

**Known judgment calls baked in:**
- Hardcoded `DEV_MANAGER_ID` in TeamPage — flagged with TODO; replaces a missing JWT-context hook.
- Duplicated `currentWeekStart()` between Tasks 5 and 6 — flagged; consolidation is Phase-2.
- DraftMode "Edit" button is a placeholder no-op (full edit UI is Phase-2 polish per the original group-11 commit-row notes). The button calls `useUpdateCommitMutation` with the current title to prove the wire works; full editor lands later.
- `__testOutcome` and `__testOutcomeId` escape-hatch props are tagged `__test*` to make their narrowness obvious. They render real UI in production with no behavior change.

**Risks I'm carrying:**
- The actual `<TeamRollup>` / `<MemberCard>` / `<IcDrawer>` / `<ReconcileTable>` / `<ReflectionField>` / `<CarryForwardRow>` / `<CarryAllButton>` prop names may not match what I wrote here verbatim. **The first task to touch each component must read the component source first** and adjust call sites. I prioritized writing real-shaped code over hand-wavy "wire it up" prose, but I haven't verified every prop name against every source file.
- `useGetTeamRollupQuery({managerId, weekStart})` — confirmed exists in `api.ts`. The response shape `RollupResponse` should include a `members` array; `<TeamRollup>` accepts a `rollup` prop (per the existing test names). If `<TeamRollup>` instead takes `members` directly, adjust.
- MSW server import path (`tests/msw/server`) is a guess from convention. The actual location may be `src/__tests__/server.ts` or in `setupTests.ts`. **First TDD task (Task 3) must verify or correct the import.**
