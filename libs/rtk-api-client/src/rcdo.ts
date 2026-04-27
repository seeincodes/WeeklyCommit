/**
 * RCDO supporting-outcome shape per ADR-0001 (stubbed).
 *
 * The hierarchy is Rally Cry > Defining Objective > Core Outcome > Supporting
 * Outcome. The list endpoint returns each row with all 4 levels inline so the
 * picker can render the breadcrumb without N+1 follow-ups.
 *
 * Per CLAUDE.md tech-stack lock these types are the canonical shape; if the
 * real RCDO contract differs the diff lands here first, then ripples through
 * the backend RcdoClient mapper and any consumers.
 *
 * Wired end-to-end via `useGetSupportingOutcomesQuery` /
 * `useGetSupportingOutcomeByIdQuery` -> backend `RcdoController` ->
 * `RcdoClient` -> upstream RCDO. The picker integration container in
 * `apps/weekly-commit-ui/src/components/RCDOPickerContainer.tsx` is the
 * primary consumer.
 */
export interface RcdoLevel {
  id: string;
  label: string;
}

export interface RcdoBreadcrumb {
  rallyCry: RcdoLevel;
  definingObjective: RcdoLevel;
  coreOutcome: RcdoLevel;
  supportingOutcome: RcdoLevel;
}

export interface SupportingOutcome {
  id: string;
  label: string;
  active: boolean;
  breadcrumb: RcdoBreadcrumb;
}
