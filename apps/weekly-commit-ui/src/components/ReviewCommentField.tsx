import { useId, useState } from 'react';
import { useCreateReviewMutation } from '@wc/rtk-api-client';

interface ReviewCommentFieldProps {
  planId: string;
  /**
   * Surfaces the IC's plan-level reviewed-at indicator. Passed in from the
   * IcDrawer's plan query; when present we render an "acknowledged on
   * <date>" indicator alongside the field. The indicator does NOT block
   * re-comment -- a manager can leave additional notes after the first
   * acknowledgement (history is in the audit log).
   */
  managerReviewedAt: string | undefined;
}

/**
 * Plan-level review comment per [MVP10]. Single textarea + Acknowledge
 * button; submission POSTs to /plans/{id}/reviews. The server side-effect
 * is to set `managerReviewedAt` on the plan; RTK Query's
 * `invalidatesTags: [{type:'Plan', id:planId}]` on the createReview
 * mutation triggers a refetch automatically so the parent drawer's
 * StateBadge + reviewed-at indicator pick up the new state.
 *
 * No comment threading in v1 (per PRD scope-out: "Per-commit comments or
 * approval gating"). The field stays usable after acknowledgement so a
 * manager can add follow-up notes; the audit log is the history.
 */
export function ReviewCommentField({ planId, managerReviewedAt }: ReviewCommentFieldProps) {
  const id = useId();
  const [comment, setComment] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [createReview, { isLoading }] = useCreateReviewMutation();

  async function handleSubmit() {
    setErrorMessage(null);
    try {
      await createReview({ planId, body: { comment } }).unwrap();
      setComment('');
    } catch {
      // The 409 case is caught globally by withConflictRetry + ConflictToast;
      // anything else surfaces inline so the manager sees their submission
      // didn't land.
      setErrorMessage('Could not submit your comment. Please try again.');
    }
  }

  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <label htmlFor={id} className="text-sm font-semibold uppercase text-gray-600">
          Comment
        </label>
        {managerReviewedAt != null && (
          <span data-testid="review-acknowledged-at" className="text-xs italic text-gray-500">
            Acknowledged {new Date(managerReviewedAt).toLocaleDateString()}
          </span>
        )}
      </div>
      <textarea
        id={id}
        aria-label="Comment"
        rows={3}
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        className="rounded border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
      />
      {errorMessage != null && (
        <div data-testid="review-comment-error" role="alert" className="text-sm text-red-700">
          {errorMessage}
        </div>
      )}
      <button
        type="button"
        disabled={isLoading || comment.trim() === ''}
        onClick={() => void handleSubmit()}
        className="self-end rounded border border-blue-500 bg-blue-500 px-3 py-1 text-sm font-medium text-white hover:bg-blue-600 disabled:cursor-not-allowed disabled:bg-blue-300 disabled:border-blue-300"
      >
        {isLoading ? 'Acknowledging…' : 'Acknowledge'}
      </button>
    </section>
  );
}
