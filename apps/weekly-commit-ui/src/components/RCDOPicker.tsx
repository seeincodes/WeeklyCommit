import { useId, useMemo, useState } from 'react';
import type { SupportingOutcome, RcdoBreadcrumb } from '@wc/rtk-api-client';

interface RCDOPickerProps {
  /**
   * Source list of supporting outcomes. The picker stays presentational; the
   * fetch lives in the `<RCDOPickerContainer />` wrapper so this component
   * remains trivially unit-testable with a fixture array.
   */
  outcomes: SupportingOutcome[];
  onSelect: (outcome: SupportingOutcome) => void;
  /**
   * When true, render the stale-cache banner -- the RCDO upstream is
   * unavailable but cached outcomes remain selectable. See MEMO Known
   * Failure Modes ("RCDO service unavailable or contract drift").
   */
  isStale?: boolean;
}

/**
 * Typeahead picker for an RCDO Supporting Outcome with the 4-level breadcrumb
 * surfaced inline. Filters the prop list locally (case-insensitive substring
 * match against the supporting outcome label).
 *
 * Per ADR-0001 the breadcrumb levels are Rally Cry > Defining Objective >
 * Core Outcome > Supporting Outcome, separated by " > ".
 */
export function RCDOPicker({ outcomes, onSelect, isStale }: RCDOPickerProps) {
  const [query, setQuery] = useState('');
  const inputId = useId();
  const listboxId = `${inputId}-listbox`;

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (q === '') return outcomes;
    return outcomes.filter((o) => o.label.toLowerCase().includes(q));
  }, [outcomes, query]);

  return (
    <div data-testid="rcdo-picker" className="flex flex-col gap-2">
      {isStale && (
        <div
          data-testid="rcdo-picker-stale-banner"
          role="status"
          className="rounded border border-yellow-300 bg-yellow-50 px-3 py-2 text-sm text-yellow-800"
        >
          RCDO is temporarily unavailable. Showing the most recent cached outcomes.
        </div>
      )}

      <label htmlFor={inputId} className="text-sm font-medium text-gray-700">
        Supporting outcome
      </label>
      <input
        id={inputId}
        type="text"
        role="combobox"
        aria-label="Supporting outcome"
        aria-controls={listboxId}
        aria-expanded={filtered.length > 0}
        aria-autocomplete="list"
        autoComplete="off"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search outcomes..."
        className="rounded border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
      />

      {filtered.length === 0 ? (
        <div data-testid="rcdo-picker-empty" className="text-sm text-gray-500">
          No outcomes match your search.
        </div>
      ) : (
        <ul id={listboxId} role="listbox" className="flex flex-col gap-1">
          {filtered.map((outcome) => (
            <li
              key={outcome.id}
              role="option"
              aria-selected={false}
              tabIndex={0}
              onClick={() => onSelect(outcome)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelect(outcome);
                }
              }}
              className="cursor-pointer rounded border border-gray-200 px-3 py-2 hover:bg-gray-50 focus:bg-gray-50 focus:outline-none"
            >
              <div className="font-medium text-gray-900">{outcome.label}</div>
              <Breadcrumb breadcrumb={outcome.breadcrumb} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Breadcrumb({ breadcrumb }: { breadcrumb: RcdoBreadcrumb }) {
  const trail = [
    breadcrumb.rallyCry.label,
    breadcrumb.definingObjective.label,
    breadcrumb.coreOutcome.label,
    breadcrumb.supportingOutcome.label,
  ].join(' › ');
  return <div className="text-xs text-gray-500">{trail}</div>;
}
