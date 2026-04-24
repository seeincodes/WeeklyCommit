package com.acme.weeklycommit.service.statemachine;

import com.acme.weeklycommit.api.exception.InvalidStateTransitionException;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle transitions for {@link WeeklyPlan}. See docs/MEMO.md decisions #3 (lives in service
 * code, never DB triggers or controllers) and #5 (three server states; reconciliation is a UI mode
 * on LOCKED past day-4).
 */
@Service
public class WeeklyPlanStateMachine {

  /** Allowed transitions. Anything not in the map is rejected. */
  private static final Map<PlanState, Set<PlanState>> ALLOWED =
      Map.of(
          PlanState.DRAFT, EnumSet.of(PlanState.LOCKED),
          PlanState.LOCKED, EnumSet.of(PlanState.RECONCILED),
          PlanState.RECONCILED, EnumSet.of(PlanState.ARCHIVED),
          PlanState.ARCHIVED, EnumSet.noneOf(PlanState.class));

  private final WeeklyPlanRepository plans;
  private final AuditLogRepository audits;
  private final Clock clock;

  public WeeklyPlanStateMachine(
      WeeklyPlanRepository plans, AuditLogRepository audits, Clock clock) {
    this.plans = plans;
    this.audits = audits;
    this.clock = clock;
  }

  @Transactional
  public WeeklyPlan transition(UUID planId, PlanState target) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));

    PlanState from = plan.getState();
    if (!ALLOWED.getOrDefault(from, Set.of()).contains(target)) {
      throw new InvalidStateTransitionException(
          from.name(), target.name(), "not allowed from " + from);
    }

    Instant now = Instant.now(clock);
    guard(plan, from, target, now);

    plan.setState(target);
    switch (target) {
      case LOCKED -> plan.setLockedAt(now);
      case RECONCILED -> plan.setReconciledAt(now);
      default -> {
        // ARCHIVED carries no separate timestamp — state itself is the record.
      }
    }

    return plans.save(plan);
  }

  /** Time-window checks beyond the transition table. Throws on violation; no-op on pass. */
  private static void guard(WeeklyPlan plan, PlanState from, PlanState target, Instant now) {
    if (from == PlanState.LOCKED && target == PlanState.RECONCILED) {
      Instant opensAt =
          plan.getWeekStart().plusDays(4).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (now.isBefore(opensAt)) {
        throw new InvalidStateTransitionException(
            from.name(), target.name(), "reconciliation window opens at " + opensAt);
      }
    }
    if (from == PlanState.RECONCILED && target == PlanState.ARCHIVED) {
      Instant eligibleAt = plan.getReconciledAt().plus(90, ChronoUnit.DAYS);
      if (now.isBefore(eligibleAt)) {
        throw new InvalidStateTransitionException(
            from.name(), target.name(), "archival eligible 90 days after reconcile: " + eligibleAt);
      }
    }
  }
}
