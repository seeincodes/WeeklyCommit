import { useState } from 'react';
import type { CreateCommitRequest, SupportingOutcome } from '@wc/rtk-api-client';
import { PlusIcon } from '../icons';
import { TIERS_ORDERED, TIER_META } from '../icons/tierMeta';
import { RCDOPickerContainer } from '../RCDOPickerContainer';

interface CommitCreateFormProps {
  /**
   * Receives the in-flight commit payload. The parent owns the actual
   * `useCreateCommitMutation` call so this component stays lifecycle-pure
   * (form state only). Returning a Promise lets the form clear only on
   * resolve; a thrown error keeps the field state intact so the user can
   * retry without retyping.
   */
  onSubmit: (payload: CreateCommitRequest) => Promise<void> | void;
  /**
   * When true, the submit button is disabled regardless of validity. Used
   * by the parent to gate submits while a previous create is still in flight.
   */
  disabled?: boolean;
}

/**
 * Commit-create form for the DRAFT-state pane. Composes the RCDOPicker
 * (via its data-fetching container), a tier select, an optional estimated-
 * hours input, and a submit button. The form holds its own field state and
 * forwards a clean `CreateCommitRequest` payload to its parent.
 *
 * The submit button stays disabled until both `title` and a supporting
 * outcome are present. After a successful submit the form clears so the IC
 * can immediately add another commit.
 *
 * The redesign keeps every accessible-name contract that DraftMode and
 * CommitCreateForm tests rely on (`getByLabelText(/title/i)`,
 * `/estimated hours/i`, `/tier/i`, `getByRole('button', { name: /add commit/i })`).
 * Visual treatment moves to the new token system: 12px gap, slate-200
 * inputs, 1px focus ring in the brand colour, primary CTA in brand-600.
 */
export function CommitCreateForm({ onSubmit, disabled = false }: CommitCreateFormProps) {
  const [title, setTitle] = useState('');
  const [outcome, setOutcome] = useState<SupportingOutcome | undefined>(undefined);
  const [tier, setTier] = useState<(typeof TIERS_ORDERED)[number]>('PEBBLE');
  const [estimatedHoursRaw, setEstimatedHoursRaw] = useState('');

  const trimmedTitle = title.trim();
  const submitDisabled = disabled || trimmedTitle === '' || outcome == null;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (submitDisabled || outcome == null) return;
    const payload: CreateCommitRequest = {
      title: trimmedTitle,
      supportingOutcomeId: outcome.id,
      chessTier: tier,
    };
    if (estimatedHoursRaw !== '') {
      const hours = Number(estimatedHoursRaw);
      if (Number.isFinite(hours)) {
        payload.estimatedHours = hours;
      }
    }
    // Fire-and-await; we own the form-clear lifecycle here so the parent's
    // mutation doesn't have to know about field state. `void` keeps the JSX
    // attribute boundary returning `void` rather than a Promise (lint rule
    // `no-misused-promises`).
    void Promise.resolve(onSubmit(payload)).then(() => {
      setTitle('');
      setOutcome(undefined);
      setTier('PEBBLE');
      setEstimatedHoursRaw('');
    });
  }

  return (
    <form
      data-testid="commit-create-form"
      onSubmit={handleSubmit}
      className="flex flex-col gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-soft-sm"
    >
      <Field label="Title">
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
          placeholder="What will you ship this week?"
          className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/30"
        />
      </Field>

      <Field label="Supporting outcome">
        <RCDOPickerContainer onSelect={setOutcome} />
        {outcome != null && (
          <div
            data-testid="commit-create-form-selected-outcome"
            className="mt-2 inline-flex items-center gap-1 rounded-md bg-brand-soft px-2 py-1 text-xs text-brand-ink"
          >
            <span className="text-meta uppercase opacity-70">Selected:</span>
            <span className="font-medium">{outcome.label}</span>
          </div>
        )}
      </Field>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Tier">
          <TierSelector value={tier} onChange={setTier} />
        </Field>

        <Field label="Estimated hours (optional)">
          <input
            type="number"
            min={0}
            step={0.5}
            value={estimatedHoursRaw}
            onChange={(e) => setEstimatedHoursRaw(e.target.value)}
            placeholder="e.g. 4"
            className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/30"
          />
        </Field>
      </div>

      <div className="flex justify-end">
        <button
          type="submit"
          disabled={submitDisabled}
          className="inline-flex items-center gap-2 rounded-md bg-brand px-4 py-2 text-sm font-semibold text-white shadow-soft-sm transition-colors hover:bg-brand-hover focus:outline-none focus:ring-2 focus:ring-brand/40 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:shadow-none"
        >
          <PlusIcon className="h-4 w-4" />
          Add commit
        </button>
      </div>
    </form>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  // Native <label> binding via wrapping keeps `getByLabelText(label)` working
  // for every input rendered inside, including the tier <select> and the
  // RCDOPicker's combobox.
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-meta uppercase text-slate-500">{label}</span>
      {children}
    </label>
  );
}

interface TierSelectorProps {
  value: (typeof TIERS_ORDERED)[number];
  onChange: (next: (typeof TIERS_ORDERED)[number]) => void;
}

/**
 * Native <select> styled to match the new token system. We keep <select>
 * (rather than a custom segmented control) because the existing
 * CommitCreateForm test exercises `user.selectOptions(getByLabelText(/tier/i),
 * 'ROCK')`, which needs an actual <select>. Using the <select> also gives
 * us the keyboard + screen-reader story for free.
 */
function TierSelector({ value, onChange }: TierSelectorProps) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as (typeof TIERS_ORDERED)[number])}
      className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/30"
    >
      {TIERS_ORDERED.map((t) => (
        <option key={t} value={t}>
          {TIER_META[t].label}
        </option>
      ))}
    </select>
  );
}
