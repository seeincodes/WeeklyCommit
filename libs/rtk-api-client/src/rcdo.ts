/**
 * RCDO supporting-outcome shape per ADR-0001 (stubbed).
 *
 * The hierarchy is Rally Cry > Defining Objective > Core Outcome > Supporting
 * Outcome. The list endpoint returns each row with all 4 levels inline so the
 * picker can render the breadcrumb without N+1 follow-ups.
 *
 * TODO(group-11-rcdo-integration): an RTK Query endpoint -- backed by a new
 * weekly-commit-service pass-through controller `GET /api/v1/rcdo/supporting-outcomes`
 * that wraps the existing backend RcdoClient -- replaces the local stub source
 * in apps/weekly-commit-ui. Until then the UI picker reads from the in-memory
 * stub and the BE endpoint sits as a follow-up subtask in TASK_LIST.md.
 *
 * Per CLAUDE.md tech-stack lock these types are the canonical shape; if the
 * real RCDO contract differs the diff lands here first, then ripples through
 * the backend RcdoClient mapper and any consumers.
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
