import { useId, useRef, type KeyboardEvent } from 'react';
import type { WeeklyCommitResponse } from '@wc/rtk-api-client';

type ActualStatus = 'PENDING' | 'DONE' | 'PARTIAL' | 'MISSED';
const STATUSES: Exclude<ActualStatus, 'PENDING'>[] = ['DONE', 'PARTIAL', 'MISSED'];

interface ReconcileTableProps {
  commits: WeeklyCommitResponse[];
  /**
   * Per-row save hook. The parent translates the patch into a
   * `PATCH /commits/{id}` mutation. Sent on every keystroke for actualNote
   * (parent typically debounces upstream); fired on radio change for
   * actualStatus. Patch shape mirrors what the backend accepts -- only
   * `actualStatus` and `actualNote` are mutable in reconcile mode per
   * PRD [MVP5].
   */
  onUpdate: (
    commitId: string,
    patch: { actualStatus?: Exclude<ActualStatus, 'PENDING'>; actualNote?: string },
  ) => void;
}

/**
 * Keyboard-first reconciliation table. One row per commit; per-row a
 * radiogroup of DONE / PARTIAL / MISSED and a textarea for the actualNote.
 * ArrowUp/ArrowDown on a status radio jumps between rows preserving the
 * column (so reviewing the same status across rows is one keystroke each).
 */
export function ReconcileTable({ commits, onUpdate }: ReconcileTableProps) {
  // Refs to every status radio keyed by `${commitId}:${status}` -- the simplest
  // way to do row-to-row arrow nav without a context provider for a single
  // self-contained component.
  const radioRefs = useRef<Record<string, HTMLInputElement | null>>({});

  if (commits.length === 0) {
    return (
      <div data-testid="reconcile-table-empty" className="text-sm text-gray-500">
        No commits to reconcile.
      </div>
    );
  }

  function handleRadioKeyDown(
    e: KeyboardEvent<HTMLInputElement>,
    rowIndex: number,
    status: string,
  ) {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    e.preventDefault();
    const targetIndex = e.key === 'ArrowDown' ? rowIndex + 1 : rowIndex - 1;
    if (targetIndex < 0 || targetIndex >= commits.length) return;
    const targetCommit = commits[targetIndex];
    if (!targetCommit) return;
    const targetRef = radioRefs.current[`${targetCommit.id}:${status}`];
    targetRef?.focus();
  }

  return (
    <table className="w-full border-collapse text-sm">
      <thead>
        <tr>
          <th className="border-b px-2 py-2 text-left">Commit</th>
          <th className="border-b px-2 py-2 text-left">Status</th>
          <th className="border-b px-2 py-2 text-left">Actual note</th>
        </tr>
      </thead>
      <tbody>
        {commits.map((c, i) => (
          <CommitRow
            key={c.id}
            commit={c}
            rowIndex={i}
            onUpdate={onUpdate}
            registerRadio={(status, el) => {
              radioRefs.current[`${c.id}:${status}`] = el;
            }}
            onRadioKeyDown={handleRadioKeyDown}
          />
        ))}
      </tbody>
    </table>
  );
}

function CommitRow({
  commit,
  rowIndex,
  onUpdate,
  registerRadio,
  onRadioKeyDown,
}: {
  commit: WeeklyCommitResponse;
  rowIndex: number;
  onUpdate: ReconcileTableProps['onUpdate'];
  registerRadio: (status: string, el: HTMLInputElement | null) => void;
  onRadioKeyDown: (e: KeyboardEvent<HTMLInputElement>, rowIndex: number, status: string) => void;
}) {
  const groupId = useId();
  const noteId = useId();

  return (
    <tr>
      <td className="border-b px-2 py-2 align-top font-medium text-gray-900">{commit.title}</td>
      <td className="border-b px-2 py-2 align-top">
        <fieldset>
          <legend className="sr-only">Status</legend>
          <div role="radiogroup" aria-label="Status" className="flex gap-3">
            {STATUSES.map((status) => (
              <label key={status} className="inline-flex items-center gap-1 text-sm">
                <input
                  type="radio"
                  ref={(el) => registerRadio(status, el)}
                  name={groupId}
                  value={status}
                  checked={commit.actualStatus === status}
                  onChange={() => onUpdate(commit.id, { actualStatus: status })}
                  onKeyDown={(e) => onRadioKeyDown(e, rowIndex, status)}
                />
                {status.charAt(0) + status.slice(1).toLowerCase()}
              </label>
            ))}
          </div>
        </fieldset>
      </td>
      <td className="border-b px-2 py-2 align-top">
        <label htmlFor={noteId} className="sr-only">
          Actual note
        </label>
        <textarea
          id={noteId}
          aria-label="Actual note"
          rows={2}
          defaultValue={commit.actualNote ?? ''}
          onChange={(e) => onUpdate(commit.id, { actualNote: e.target.value })}
          className="w-full rounded border border-gray-300 px-2 py-1 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
        />
      </td>
    </tr>
  );
}
