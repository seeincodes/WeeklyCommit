package com.acme.weeklycommit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import com.acme.weeklycommit.service.statemachine.WeeklyPlanStateMachine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoLockJobTest {

  @Mock private WeeklyPlanRepository plans;
  @Mock private WeeklyPlanStateMachine stateMachine;

  private AutoLockJob job(Clock clock, int cutoffHours) {
    return new AutoLockJob(plans, stateMachine, clock, cutoffHours);
  }

  /**
   * Wednesday 12:00 UTC, cutoff = 36h. Week-start of "Monday 36h before now" = the Monday whose
   * Monday 00:00 UTC is &le; (now - 36h) = Tuesday 00:00 UTC. So plans for week starting last
   * Monday (or earlier) qualify.
   */
  @Test
  void runOnce_transitionsAllDraftPlansPastCutoff() {
    // Wed 2026-04-29 12:00Z; cutoff = 36h -> last Monday 2026-04-27 00:00 UTC qualifies.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T12:00:00Z"), ZoneId.of("UTC"));
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    WeeklyPlan plan1 = new WeeklyPlan(p1, UUID.randomUUID(), LocalDate.parse("2026-04-20"));
    WeeklyPlan plan2 = new WeeklyPlan(p2, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findDraftsPastCutoff(eq(PlanState.DRAFT), eq(LocalDate.parse("2026-04-27"))))
        .thenReturn(List.of(plan1, plan2));

    int locked = job(clock, 36).runOnce();

    assertThat(locked).isEqualTo(2);
    verify(stateMachine).transition(eq(p1), eq(PlanState.LOCKED), isNull());
    verify(stateMachine).transition(eq(p2), eq(PlanState.LOCKED), isNull());
  }

  @Test
  void runOnce_emptyResult_noTransitions() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T12:00:00Z"), ZoneId.of("UTC"));
    when(plans.findDraftsPastCutoff(eq(PlanState.DRAFT), eq(LocalDate.parse("2026-04-27"))))
        .thenReturn(List.of());

    int locked = job(clock, 36).runOnce();

    assertThat(locked).isEqualTo(0);
    verify(stateMachine, never())
        .transition(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void runOnce_singleFailure_doesNotAbortRemainingTransitions() {
    // One bad plan should not stop the rest of the batch -- otherwise a single optimistic-lock
    // failure on plan 1 would block plan 2..N from auto-locking until the next hour.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T12:00:00Z"), ZoneId.of("UTC"));
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    WeeklyPlan plan1 = new WeeklyPlan(p1, UUID.randomUUID(), LocalDate.parse("2026-04-20"));
    WeeklyPlan plan2 = new WeeklyPlan(p2, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findDraftsPastCutoff(eq(PlanState.DRAFT), eq(LocalDate.parse("2026-04-27"))))
        .thenReturn(List.of(plan1, plan2));
    when(stateMachine.transition(eq(p1), eq(PlanState.LOCKED), isNull()))
        .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("plan", p1));
    when(stateMachine.transition(eq(p2), eq(PlanState.LOCKED), isNull())).thenReturn(plan2);

    int locked = job(clock, 36).runOnce();

    assertThat(locked).isEqualTo(1);
    verify(stateMachine).transition(eq(p2), eq(PlanState.LOCKED), isNull());
  }

  @Test
  void runOnce_cutoffMath_36h_beforeMondayBoundary_returnsPriorMonday() {
    // Mon 2026-04-27 11:00Z, 36h cutoff -> now - 36h = Sat 2026-04-25 23:00Z.
    // The Monday <= that = 2026-04-20.
    Clock clock = Clock.fixed(Instant.parse("2026-04-27T11:00:00Z"), ZoneId.of("UTC"));
    when(plans.findDraftsPastCutoff(eq(PlanState.DRAFT), eq(LocalDate.parse("2026-04-20"))))
        .thenReturn(List.of());

    job(clock, 36).runOnce();

    // verification implicit -- if the cutoff was wrong, the stub wouldn't match and
    // findDraftsPastCutoff would return null/throw.
  }
}
