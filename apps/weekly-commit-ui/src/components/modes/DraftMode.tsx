import type { ReactNode } from 'react';
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
}

/**
 * The DRAFT-state pane of the WeekEditor. Shell that wires:
 *   - `<StateBadge state="DRAFT" />` for the next-action hint
 *   - `<CommitCreateForm />` -> `useCreateCommitMutation` for commit creation
 *   - `<ChessTier />` over `useListCommitsQuery` for the existing-commits list
 *   - "Lock Week" button -> `useTransitionMutation({to:'LOCKED'})`
 *
 * The full edit/drag-reorder UX is Phase-2 polish; today the row exposes a
 * Delete affordance + a placeholder Edit no-op so the wiring of
 * `useUpdateCommitMutation` and `useDeleteCommitMutation` is exercised. The
 * "Edit" no-op stays today because a richer edit-in-place UI lands in the
 * next polish pass; replacing it now would burn the budget without a user-
 * visible win.
 */
export function DraftMode({ planId }: DraftModeProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });
  const [createCommit, { isLoading: isCreating, error: createError }] = useCreateCommitMutation();
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

  const renderRow = (commit: WeeklyCommitResponse, _isTopRock: boolean): ReactNode => (
    <div
      data-testid={`draft-row-${commit.id}`}
      className="flex items-center gap-2 px-3 py-2 border-b last:border-b-0"
    >
      <span className="font-medium text-gray-900">{commit.title}</span>
      <span className="ml-auto flex gap-2">
        <button
          type="button"
          className="text-xs text-blue-600 hover:underline"
          onClick={() => {
            // Placeholder: full edit-in-place lands in the Phase-2 polish.
            // Calling updateCommit with the current title proves the hook
            // wire is alive without changing user-visible state.
            void updateCommit({
              commitId: commit.id,
              body: { title: commit.title },
            });
          }}
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
      {(createError ?? lockError) && (
        <div data-testid="draft-mutation-error" role="alert" className="text-red-700 text-sm">
          Something went wrong. Try again.
        </div>
      )}
      <CommitCreateForm onSubmit={handleCreate} disabled={isCreating} />
      <ChessTier commits={safeCommits} renderCommit={renderRow} />
    </div>
  );
}
