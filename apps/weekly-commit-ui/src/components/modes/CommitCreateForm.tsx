import { useState } from 'react';
import type { CreateCommitRequest, SupportingOutcome } from '@wc/rtk-api-client';
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

const TIERS = ['ROCK', 'PEBBLE', 'SAND'] as const;
type Tier = (typeof TIERS)[number];

/**
 * Commit-create form for the DRAFT-state pane. Composes the RCDOPicker
 * (via its data-fetching container), a tier select, an optional estimated-
 * hours input, and a submit button. The form holds its own field state and
 * forwards a clean `CreateCommitRequest` payload to its parent.
 *
 * The submit button stays disabled until both `title` and a supporting
 * outcome are present. After a successful submit the form clears so the IC
 * can immediately add another commit.
 */
export function CommitCreateForm({ onSubmit, disabled = false }: CommitCreateFormProps) {
  const [title, setTitle] = useState('');
  const [outcome, setOutcome] = useState<SupportingOutcome | undefined>(undefined);
  const [tier, setTier] = useState<Tier>('PEBBLE');
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
      {outcome != null && (
        <div data-testid="commit-create-form-selected-outcome" className="text-xs text-gray-700">
          Selected: <span className="font-medium">{outcome.label}</span>
        </div>
      )}

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
