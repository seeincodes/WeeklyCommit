import { useCreateCurrentForMeMutation } from '@wc/rtk-api-client';

/**
 * Empty-state pane for the WeekEditor when no plan exists for the current week
 * (the GET /plans/me/current endpoint returned 404). Per MEMO #10 + USER_FLOW
 * flow 1, plan creation is *explicit* -- a button click, not a side-effect of
 * route navigation.
 *
 * Clicking "Create plan" fires POST /plans; RTK Query invalidates the Plan
 * LIST tag, useGetCurrentForMeQuery refetches, and WeekEditor flips into
 * DraftMode without any local state plumbing.
 */
export function BlankState() {
  const [createPlan, { isLoading, error }] = useCreateCurrentForMeMutation();

  return (
    <div data-testid="week-editor-blank" className="flex flex-col gap-3">
      <p>You haven’t started this week yet.</p>
      <button
        type="button"
        onClick={() => void createPlan()}
        disabled={isLoading}
        className="self-start rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300"
      >
        {isLoading ? 'Creating…' : 'Create plan'}
      </button>
      {error && (
        <div data-testid="blank-state-error" role="alert" className="text-sm text-red-700">
          Couldn’t create the plan. Try again.
        </div>
      )}
    </div>
  );
}
