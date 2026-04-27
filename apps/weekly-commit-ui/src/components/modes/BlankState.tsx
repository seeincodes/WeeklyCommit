/**
 * Empty-state pane for the WeekEditor when no plan exists for the current week
 * (the GET /plans/me/current endpoint returned 404). Per MEMO #10 + USER_FLOW
 * flow 1, plan creation is *explicit* -- a button click, not a side-effect of
 * route navigation -- so this pane surfaces a clear "Create plan" CTA.
 *
 * The button wiring (POST /plans via useCreateCurrentForMeMutation) lands in
 * group 13b subtask 1 alongside the rest of the DraftMode wiring. For now the
 * button is a presentational stub.
 */
export function BlankState() {
  return (
    <div data-testid="week-editor-blank" className="flex flex-col gap-3">
      <p>You haven’t started this week yet.</p>
      <button type="button">Create plan</button>
    </div>
  );
}
