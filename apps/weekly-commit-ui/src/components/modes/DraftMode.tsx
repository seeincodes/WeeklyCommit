import type { ReactNode } from 'react';
import {
  useCreateCommitMutation,
  useDeleteCommitMutation,
  useListCommitsQuery,
  useTransitionMutation,
  type CreateCommitRequest,
  type WeeklyCommitResponse,
} from '@wc/rtk-api-client';
import { ChessTier } from '../ChessTier';
import { StateBadge } from '../StateBadge';
import { LockIcon, SparkleIcon, TrashIcon } from '../icons';
import { TIER_META } from '../icons/tierMeta';
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
 * Each existing commit row exposes a Delete affordance. The previous "Edit"
 * placeholder no-op was removed in the group-19 redesign -- a button that
 * pretends to be functional but only re-saves the current title broke
 * trust on first click. Real inline-edit lands as its own feature, not a
 * polish item; deferring it cleanly is better than shipping a dishonest
 * affordance.
 */
export function DraftMode({ planId }: DraftModeProps) {
  const { data: commits, isLoading, error } = useListCommitsQuery({ planId });
  const [createCommit, { isLoading: isCreating, error: createError }] = useCreateCommitMutation();
  const [deleteCommit] = useDeleteCommitMutation();
  const [transition, { isLoading: isLocking, error: lockError }] = useTransitionMutation();

  if (isLoading) {
    return (
      <div data-testid="draft-loading" className="text-slate-500">
        Loading…
      </div>
    );
  }
  if (error) {
    return (
      <div
        data-testid="draft-error"
        role="alert"
        className="rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-ink"
      >
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

  const renderRow = (commit: WeeklyCommitResponse): ReactNode => (
    <DraftRow commit={commit} onDelete={() => void deleteCommit({ commitId: commit.id })} />
  );

  return (
    <div data-testid="week-editor-draft" className="flex flex-col gap-6">
      <DraftHeader isLocking={isLocking} onLock={handleLock} />
      {(createError ?? lockError) && (
        <div
          data-testid="draft-mutation-error"
          role="alert"
          className="rounded-md border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-ink"
        >
          Something went wrong. Try again.
        </div>
      )}
      <Section
        title="Add a commit"
        description="Pick a Supporting Outcome, name what you’ll deliver, and choose its weight."
        icon={<SparkleIcon className="h-4 w-4 text-brand" />}
      >
        <CommitCreateForm onSubmit={handleCreate} disabled={isCreating} />
      </Section>
      <Section
        title="Your week"
        description={
          safeCommits.length === 0
            ? 'Commits you add will land in the chess spine below.'
            : `${safeCommits.length} ${safeCommits.length === 1 ? 'commit' : 'commits'} queued.`
        }
      >
        <ChessTier commits={safeCommits} renderCommit={(c) => renderRow(c)} />
      </Section>
    </div>
  );
}

interface DraftHeaderProps {
  isLocking: boolean;
  onLock: () => void;
}

function DraftHeader({ isLocking, onLock }: DraftHeaderProps) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-5 shadow-soft-sm sm:flex-row sm:items-center sm:justify-between">
      <StateBadge state="DRAFT" isReconcileEligible={false} />
      <button
        type="button"
        onClick={onLock}
        disabled={isLocking}
        className="inline-flex items-center justify-center gap-2 rounded-md bg-brand px-4 py-2 text-sm font-semibold text-white shadow-soft-sm transition-colors hover:bg-brand-hover focus:outline-none focus:ring-2 focus:ring-brand/40 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:shadow-none"
      >
        <LockIcon className="h-4 w-4" />
        {isLocking ? 'Locking…' : 'Lock Week'}
      </button>
    </div>
  );
}

interface SectionProps {
  title: string;
  description?: string;
  icon?: ReactNode;
  children: ReactNode;
}

function Section({ title, description, icon, children }: SectionProps) {
  return (
    <section className="flex flex-col gap-3">
      <header className="flex items-baseline gap-2">
        {icon != null && <span className="self-center">{icon}</span>}
        <h2 className="text-title text-slate-900">{title}</h2>
        {description != null && <p className="text-sm text-slate-500">{description}</p>}
      </header>
      {children}
    </section>
  );
}

interface DraftRowProps {
  commit: WeeklyCommitResponse;
  onDelete: () => void;
}

function DraftRow({ commit, onDelete }: DraftRowProps) {
  const meta = TIER_META[commit.chessTier];
  const Icon = meta.Icon;
  return (
    <div
      data-testid={`draft-row-${commit.id}`}
      className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-soft-sm"
    >
      <span
        className={`flex h-8 w-8 flex-none items-center justify-center rounded-md ${meta.chipBg} ${meta.ink}`}
        aria-hidden
      >
        <Icon className="h-4 w-4" />
      </span>
      <span className="flex-1 text-sm font-medium text-slate-900">{commit.title}</span>
      <button
        type="button"
        onClick={onDelete}
        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-slate-500 transition-colors hover:bg-danger-soft hover:text-danger focus:outline-none focus:ring-2 focus:ring-danger/30"
        aria-label={`Delete ${commit.title}`}
      >
        <TrashIcon className="h-3.5 w-3.5" />
        Delete
      </button>
    </div>
  );
}
