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
    <div data-testid="week-editor-blank" className="flex flex-col items-start gap-4 py-2">
      <div className="flex flex-col gap-1.5">
        <h2 className="text-xl font-semibold text-gray-900">A fresh week.</h2>
        <p className="max-w-prose text-sm text-gray-600">
          What would make this week a win? Create a plan to capture your Top Rock and the supporting
          moves underneath it.
        </p>
      </div>
      <button
        type="button"
        onClick={() => void createPlan()}
        disabled={isLoading}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700 disabled:bg-gray-300"
      >
        {isLoading ? 'Creating…' : 'Create plan'}
      </button>
      {error && (
        <div data-testid="blank-state-error" role="alert" className="text-sm text-red-700">
          Couldn’t create the plan. Try again in a moment.
        </div>
      )}
    </div>
  );
}
