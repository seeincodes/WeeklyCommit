package com.acme.weeklycommit.service.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanStateMachineTest {

  private static final Instant FROZEN_NOW = Instant.parse("2026-05-01T12:00:00Z");
  private final Clock fixedClock = Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);

  @Mock private WeeklyPlanRepository plans;
  @Mock private AuditLogRepository audits;

  private WeeklyPlanStateMachine machine() {
    return new WeeklyPlanStateMachine(plans, audits, fixedClock);
  }

  @Test
  void transition_draftToLocked_setsStateAndLockedAt() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan draft = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(draft));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    WeeklyPlan result = machine().transition(planId, PlanState.LOCKED);

    assertThat(result.getState()).isEqualTo(PlanState.LOCKED);
    assertThat(result.getLockedAt()).isEqualTo(FROZEN_NOW);
    verify(plans).save(draft);
  }

  @Test
  void transition_planNotFound_throwsResourceNotFound() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> machine().transition(planId, PlanState.LOCKED))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(planId.toString());
  }

  @Test
  void transition_draftToReconciled_rejected_mustPassThroughLocked() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan draft = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> machine().transition(planId, PlanState.RECONCILED))
        .isInstanceOf(InvalidStateTransitionException.class)
        .matches(e -> ((InvalidStateTransitionException) e).getFromState().equals("DRAFT"))
        .matches(e -> ((InvalidStateTransitionException) e).getToState().equals("RECONCILED"));
  }

  @Test
  void transition_lockedToReconciled_setsStateAndReconciledAt() {
    // weekStart 2026-04-27 => reconciliation opens 2026-05-01T00:00Z.
    // FROZEN_NOW = 2026-05-01T12:00Z => window has opened.
    UUID planId = UUID.randomUUID();
    WeeklyPlan locked = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    locked.setState(PlanState.LOCKED);
    locked.setLockedAt(Instant.parse("2026-04-27T17:00:00Z"));
    when(plans.findById(planId)).thenReturn(Optional.of(locked));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    WeeklyPlan result = machine().transition(planId, PlanState.RECONCILED);

    assertThat(result.getState()).isEqualTo(PlanState.RECONCILED);
    assertThat(result.getReconciledAt()).isEqualTo(FROZEN_NOW);
  }

  @Test
  void transition_lockedToReconciled_rejectedBeforeDay4() {
    // weekStart 2026-04-28 => reconciliation opens 2026-05-02T00:00Z.
    // FROZEN_NOW = 2026-05-01T12:00Z => still closed; guard must fire.
    UUID planId = UUID.randomUUID();
    WeeklyPlan locked = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-28"));
    locked.setState(PlanState.LOCKED);
    locked.setLockedAt(Instant.parse("2026-04-28T17:00:00Z"));
    when(plans.findById(planId)).thenReturn(Optional.of(locked));

    assertThatThrownBy(() -> machine().transition(planId, PlanState.RECONCILED))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("reconciliation");
  }

  @Test
  void transition_reconciledToArchived_rejectedBefore90Days() {
    // reconciledAt 89 days ago relative to FROZEN_NOW -> archival guard must fire.
    UUID planId = UUID.randomUUID();
    WeeklyPlan reconciled =
        new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-01-12"));
    reconciled.setState(PlanState.RECONCILED);
    reconciled.setReconciledAt(FROZEN_NOW.minus(89, ChronoUnit.DAYS));
    when(plans.findById(planId)).thenReturn(Optional.of(reconciled));

    assertThatThrownBy(() -> machine().transition(planId, PlanState.ARCHIVED))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("archival");
  }

  @Test
  void transition_appendsAuditLogRow_withFromAndToStates() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan draft = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(draft));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    when(audits.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

    machine().transition(planId, PlanState.LOCKED);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(audits).save(captor.capture());

    AuditLog row = captor.getValue();
    assertThat(row.getEntityType()).isEqualTo(AuditEntityType.WEEKLY_PLAN);
    assertThat(row.getEntityId()).isEqualTo(planId);
    assertThat(row.getEventType()).isEqualTo(AuditEventType.STATE_TRANSITION);
    assertThat(row.getFromState()).isEqualTo("DRAFT");
    assertThat(row.getToState()).isEqualTo("LOCKED");
  }

  @Test
  void transition_idempotentNoop_doesNotAudit() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan alreadyLocked =
        new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    alreadyLocked.setState(PlanState.LOCKED);
    alreadyLocked.setLockedAt(Instant.parse("2026-04-27T17:00:00Z"));
    when(plans.findById(planId)).thenReturn(Optional.of(alreadyLocked));

    machine().transition(planId, PlanState.LOCKED);

    verify(audits, never()).save(any(AuditLog.class));
  }

  @Test
  void transition_alreadyInTargetState_isIdempotentNoop() {
    // Retry-safety: repeated transition with the same target returns the plan unchanged,
    // does NOT persist, does NOT re-emit notifications. Presearch §7 idempotency key is
    // effectively (plan_id, target_state, version) — matching state + version means "already done".
    UUID planId = UUID.randomUUID();
    WeeklyPlan alreadyLocked =
        new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    alreadyLocked.setState(PlanState.LOCKED);
    Instant originallyLockedAt = Instant.parse("2026-04-27T17:00:00Z");
    alreadyLocked.setLockedAt(originallyLockedAt);
    when(plans.findById(planId)).thenReturn(Optional.of(alreadyLocked));

    WeeklyPlan result = machine().transition(planId, PlanState.LOCKED);

    assertThat(result.getState()).isEqualTo(PlanState.LOCKED);
    // lockedAt NOT overwritten with FROZEN_NOW -- retained from the original transition
    assertThat(result.getLockedAt()).isEqualTo(originallyLockedAt);
    verify(plans, never()).save(any(WeeklyPlan.class));
  }
}
