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
  private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private final Clock fixedClock = Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);

  @Mock private WeeklyPlanRepository plans;
  @Mock private AuditLogRepository audits;
  @Mock private NotificationDispatcher dispatcher;

  private WeeklyPlanStateMachine machine() {
    return new WeeklyPlanStateMachine(plans, audits, dispatcher, fixedClock);
  }

  private static WeeklyPlan draftPlan(UUID planId, LocalDate weekStart) {
    return new WeeklyPlan(planId, UUID.randomUUID(), weekStart);
  }

  private static WeeklyPlan lockedPlan(UUID planId, LocalDate weekStart) {
    WeeklyPlan p = draftPlan(planId, weekStart);
    p.setState(PlanState.LOCKED);
    p.setLockedAt(weekStart.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
    return p;
  }

  @Test
  void transition_draftToLocked_setsStateAndLockedAt() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan draft = draftPlan(planId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(draft));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    WeeklyPlan result = machine().transition(planId, PlanState.LOCKED, ACTOR);

    assertThat(result.getState()).isEqualTo(PlanState.LOCKED);
    assertThat(result.getLockedAt()).isEqualTo(FROZEN_NOW);
    verify(plans).save(draft);
  }

  @Test
  void transition_planNotFound_throwsResourceNotFound() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> machine().transition(planId, PlanState.LOCKED, ACTOR))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(planId.toString());
  }

  @Test
  void transition_draftToReconciled_rejected_mustPassThroughLocked() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId))
        .thenReturn(Optional.of(draftPlan(planId, LocalDate.parse("2026-04-27"))));

    assertThatThrownBy(() -> machine().transition(planId, PlanState.RECONCILED, ACTOR))
        .isInstanceOf(InvalidStateTransitionException.class)
        .matches(e -> ((InvalidStateTransitionException) e).getFromState().equals("DRAFT"))
        .matches(e -> ((InvalidStateTransitionException) e).getToState().equals("RECONCILED"));
  }

  @Test
  void transition_lockedToReconciled_setsStateAndReconciledAt() {
    // weekStart 2026-04-27 => reconciliation opens 2026-05-01T00:00Z.
    // FROZEN_NOW = 2026-05-01T12:00Z => window has opened.
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId))
        .thenReturn(Optional.of(lockedPlan(planId, LocalDate.parse("2026-04-27"))));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    WeeklyPlan result = machine().transition(planId, PlanState.RECONCILED, ACTOR);

    assertThat(result.getState()).isEqualTo(PlanState.RECONCILED);
    assertThat(result.getReconciledAt()).isEqualTo(FROZEN_NOW);
  }

  @Test
  void transition_lockedToReconciled_rejectedBeforeDay4() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId))
        .thenReturn(Optional.of(lockedPlan(planId, LocalDate.parse("2026-04-28"))));

    assertThatThrownBy(() -> machine().transition(planId, PlanState.RECONCILED, ACTOR))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("reconciliation");
  }

  @Test
  void transition_reconciledToArchived_rejectedBefore90Days() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan reconciled = draftPlan(planId, LocalDate.parse("2026-01-12"));
    reconciled.setState(PlanState.RECONCILED);
    reconciled.setReconciledAt(FROZEN_NOW.minus(89, ChronoUnit.DAYS));
    when(plans.findById(planId)).thenReturn(Optional.of(reconciled));

    assertThatThrownBy(() -> machine().transition(planId, PlanState.ARCHIVED, ACTOR))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("archival");
  }

  @Test
  void transition_appendsAuditLogRow_withFromAndToStates_andActorId() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan draft = draftPlan(planId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(draft));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    when(audits.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

    machine().transition(planId, PlanState.LOCKED, ACTOR);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(audits).save(captor.capture());

    AuditLog row = captor.getValue();
    assertThat(row.getEntityType()).isEqualTo(AuditEntityType.WEEKLY_PLAN);
    assertThat(row.getEntityId()).isEqualTo(planId);
    assertThat(row.getEventType()).isEqualTo(AuditEventType.STATE_TRANSITION);
    assertThat(row.getFromState()).isEqualTo("DRAFT");
    assertThat(row.getToState()).isEqualTo("LOCKED");
    assertThat(row.getActorId()).isEqualTo(ACTOR);
  }

  @Test
  void transition_systemInitiated_auditRowHasNullActor() {
    // Scheduled jobs (auto-lock, archival) call with a null actor; audit row
    // reflects that faithfully — downstream can filter "actor IS NULL" for system events.
    UUID planId = UUID.randomUUID();
    WeeklyPlan draft = draftPlan(planId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(draft));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    when(audits.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

    machine().transition(planId, PlanState.LOCKED, null);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(audits).save(captor.capture());
    assertThat(captor.getValue().getActorId()).isNull();
  }

  @Test
  void transition_idempotentNoop_doesNotAudit() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId))
        .thenReturn(Optional.of(lockedPlan(planId, LocalDate.parse("2026-04-27"))));

    machine().transition(planId, PlanState.LOCKED, ACTOR);

    verify(audits, never()).save(any(AuditLog.class));
  }

  @Test
  void transition_dispatchesNotificationEvent_afterStateChange() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan draft = draftPlan(planId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(draft));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    machine().transition(planId, PlanState.LOCKED, ACTOR);

    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(dispatcher).dispatchAfterCommit(captor.capture());
    NotificationEvent event = captor.getValue();
    assertThat(event.planId()).isEqualTo(planId);
    assertThat(event.from()).isEqualTo(PlanState.DRAFT);
    assertThat(event.to()).isEqualTo(PlanState.LOCKED);
  }

  @Test
  void transition_idempotentNoop_doesNotDispatch() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId))
        .thenReturn(Optional.of(lockedPlan(planId, LocalDate.parse("2026-04-27"))));

    machine().transition(planId, PlanState.LOCKED, ACTOR);

    verify(dispatcher, never()).dispatchAfterCommit(any(NotificationEvent.class));
  }

  @Test
  void transition_alreadyInTargetState_isIdempotentNoop() {
    // Retry-safety: repeated transition with the same target returns the plan unchanged,
    // does NOT persist, does NOT re-emit notifications.
    UUID planId = UUID.randomUUID();
    WeeklyPlan alreadyLocked = lockedPlan(planId, LocalDate.parse("2026-04-27"));
    Instant originallyLockedAt = alreadyLocked.getLockedAt();
    when(plans.findById(planId)).thenReturn(Optional.of(alreadyLocked));

    WeeklyPlan result = machine().transition(planId, PlanState.LOCKED, ACTOR);

    assertThat(result.getState()).isEqualTo(PlanState.LOCKED);
    assertThat(result.getLockedAt()).isEqualTo(originallyLockedAt);
    verify(plans, never()).save(any(WeeklyPlan.class));
  }
}
