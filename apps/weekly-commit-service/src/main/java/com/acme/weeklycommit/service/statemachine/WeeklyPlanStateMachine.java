package com.acme.weeklycommit.service.statemachine;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle transitions for {@link WeeklyPlan}. See docs/MEMO.md decisions #3 (this lives in
 * service code, never in DB triggers or controllers) and #5 (three server states; reconciliation
 * is a UI mode on LOCKED past day-4).
 *
 * <p>Built red-green; surface grows as tests demand.
 */
@Service
public class WeeklyPlanStateMachine {

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

    if (target == PlanState.LOCKED) {
      plan.setState(PlanState.LOCKED);
      plan.setLockedAt(Instant.now(clock));
    }

    return plans.save(plan);
  }
}
