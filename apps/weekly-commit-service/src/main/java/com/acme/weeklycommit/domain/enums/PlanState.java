package com.acme.weeklycommit.domain.enums;

/**
 * Lifecycle state of a {@code WeeklyPlan}. Transitions governed by {@code WeeklyPlanStateMachine}
 * (group 5). See docs/MEMO.md decision #5 on why RECONCILING is collapsed.
 */
public enum PlanState {
  DRAFT,
  LOCKED,
  RECONCILED,
  ARCHIVED
}
