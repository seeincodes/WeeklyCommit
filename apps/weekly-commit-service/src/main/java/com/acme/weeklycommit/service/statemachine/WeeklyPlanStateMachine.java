package com.acme.weeklycommit.service.statemachine;

import com.acme.weeklycommit.api.exception.InvalidStateTransitionException;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.domain.enums.AuditEventType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle transitions for {@link WeeklyPlan}. See docs/MEMO.md decisions #3 (lives in service
 * code, never DB triggers or controllers) and #5 (three server states; reconciliation is a UI mode
 * on LOCKED past day-4).
 */
@Service
public class WeeklyPlanStateMachine {

  private static final Logger log = LoggerFactory.getLogger(WeeklyPlanStateMachine.class);

  /** Allowed transitions. Anything not in the map is rejected. */
  private static final Map<PlanState, Set<PlanState>> ALLOWED =
      Map.of(
          PlanState.DRAFT, EnumSet.of(PlanState.LOCKED),
          PlanState.LOCKED, EnumSet.of(PlanState.RECONCILED),
          PlanState.RECONCILED, EnumSet.of(PlanState.ARCHIVED),
          PlanState.ARCHIVED, EnumSet.noneOf(PlanState.class));

  private final WeeklyPlanRepository plans;
  private final AuditLogRepository audits;
  private final NotificationDispatcher dispatcher;
  private final Clock clock;

  public WeeklyPlanStateMachine(
      WeeklyPlanRepository plans,
      AuditLogRepository audits,
      NotificationDispatcher dispatcher,
      Clock clock) {
    this.plans = plans;
    this.audits = audits;
    this.dispatcher = dispatcher;
    this.clock = clock;
  }

  /**
   * Transition a plan to the given target state.
   *
   * @param planId plan to transition
   * @param target target {@link PlanState}
   * @param actorId employee UUID of the caller, or {@code null} for system-initiated transitions
   *     (scheduled jobs). Threaded into the {@code audit_log} row so human activity and system
   *     activity are separable downstream.
   */
  @Transactional
  public WeeklyPlan transition(UUID planId, PlanState target, UUID actorId) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));

    // Idempotent no-op: already in the target state (retry-safety).
    if (plan.getState() == target) {
      log.debug("transition no-op: plan {} already in state {}", planId, target);
      return plan;
    }

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
      case ARCHIVED, DRAFT -> {
        // ARCHIVED: terminal state; the state column is the record. DRAFT is unreachable as a
        // target via the transition table (no predecessor points to it) but must be covered to
        // keep the switch exhaustive.
      }
    }

    WeeklyPlan saved = plans.save(plan);
    appendAudit(planId, from, target, actorId);
    dispatcher.dispatchAfterCommit(new NotificationEvent(planId, from, target, saved.getVersion()));
    log.info(
        "transition: plan={} {} -> {} actor={} v{}",
        planId,
        from,
        target,
        actorId == null ? "system" : actorId,
        saved.getVersion());
    return saved;
  }

  /**
   * Append one {@code audit_log} row inside the same transaction as the plan save. Audit write
   * failure rolls the whole transition back — provenance is coupled to state change by design.
   */
  private void appendAudit(UUID planId, PlanState from, PlanState target, UUID actorId) {
    AuditLog row =
        new AuditLog(
            UUID.randomUUID(),
            AuditEntityType.WEEKLY_PLAN,
            planId,
            AuditEventType.STATE_TRANSITION,
            actorId);
    row.setFromState(from.name());
    row.setToState(target.name());
    audits.save(row);
  }

  /** Time-window checks beyond the transition table. Throws on violation; no-op on pass. */
  private static void guard(WeeklyPlan plan, PlanState from, PlanState target, Instant now) {
    if (from == PlanState.LOCKED && target == PlanState.RECONCILED) {
      Instant opensAt = plan.getWeekStart().plusDays(4).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (now.isBefore(opensAt)) {
        throw new InvalidStateTransitionException(
            from.name(), target.name(), "reconciliation window opens at " + opensAt);
      }
    }
    if (from == PlanState.RECONCILED && target == PlanState.ARCHIVED) {
      Instant reconciledAt = plan.getReconciledAt();
      if (reconciledAt == null) {
        throw new IllegalStateException(
            "invariant violated: RECONCILED plan " + plan.getId() + " has null reconciledAt");
      }
      Instant eligibleAt = reconciledAt.plus(90, ChronoUnit.DAYS);
      if (now.isBefore(eligibleAt)) {
        throw new InvalidStateTransitionException(
            from.name(), target.name(), "archival eligible 90 days after reconcile: " + eligibleAt);
      }
    }
  }
}
