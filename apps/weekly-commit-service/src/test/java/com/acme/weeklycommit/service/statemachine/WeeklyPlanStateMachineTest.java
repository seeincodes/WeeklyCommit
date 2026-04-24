package com.acme.weeklycommit.service.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
