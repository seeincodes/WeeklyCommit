package com.acme.weeklycommit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import com.acme.weeklycommit.service.statemachine.WeeklyPlanStateMachine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArchivalJobTest {

  @Mock private WeeklyPlanRepository plans;
  @Mock private WeeklyPlanStateMachine stateMachine;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final JobMetrics jobMetrics = new JobMetrics(meterRegistry);

  private ArchivalJob job(Clock clock, int olderThanDays) {
    return new ArchivalJob(plans, stateMachine, clock, jobMetrics, olderThanDays);
  }

  @Test
  void runOnce_archivesAllReconciledPlansBeforeThreshold() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T02:00:00Z"), ZoneId.of("UTC"));
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    WeeklyPlan plan1 = new WeeklyPlan(p1, UUID.randomUUID(), LocalDate.parse("2026-01-05"));
    WeeklyPlan plan2 = new WeeklyPlan(p2, UUID.randomUUID(), LocalDate.parse("2026-01-12"));
    when(plans.findReconciledBefore(eq(PlanState.RECONCILED), any(Instant.class)))
        .thenReturn(List.of(plan1, plan2));

    int archived = job(clock, 90).runOnce();

    assertThat(archived).isEqualTo(2);
    verify(stateMachine).transition(eq(p1), eq(PlanState.ARCHIVED), isNull());
    verify(stateMachine).transition(eq(p2), eq(PlanState.ARCHIVED), isNull());
  }

  @Test
  void runOnce_emptyResult_noTransitions() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T02:00:00Z"), ZoneId.of("UTC"));
    when(plans.findReconciledBefore(eq(PlanState.RECONCILED), any(Instant.class)))
        .thenReturn(List.of());

    int archived = job(clock, 90).runOnce();

    assertThat(archived).isEqualTo(0);
    verify(stateMachine, never()).transition(any(), any(), any());
  }

  @Test
  void runOnce_singleFailure_doesNotAbortBatch() {
    // One optimistic-lock conflict on plan 1 must not block plan 2's archival -- the next nightly
    // tick will just retry plan 1 if it's still RECONCILED past the threshold.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T02:00:00Z"), ZoneId.of("UTC"));
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    WeeklyPlan plan1 = new WeeklyPlan(p1, UUID.randomUUID(), LocalDate.parse("2026-01-05"));
    WeeklyPlan plan2 = new WeeklyPlan(p2, UUID.randomUUID(), LocalDate.parse("2026-01-12"));
    when(plans.findReconciledBefore(eq(PlanState.RECONCILED), any(Instant.class)))
        .thenReturn(List.of(plan1, plan2));
    when(stateMachine.transition(eq(p1), eq(PlanState.ARCHIVED), isNull()))
        .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("plan", p1));
    when(stateMachine.transition(eq(p2), eq(PlanState.ARCHIVED), isNull())).thenReturn(plan2);

    int archived = job(clock, 90).runOnce();

    assertThat(archived).isEqualTo(1);
    verify(stateMachine).transition(eq(p2), eq(PlanState.ARCHIVED), isNull());
  }

  @Test
  void runOnce_thresholdMath_subtracts90Days() {
    // The Instant passed to findReconciledBefore must be exactly clock.instant() - 90 days.
    // SQL never sees NOW(); the application computes the cutoff (MEMO: week math at service layer).
    Instant fixedNow = Instant.parse("2026-04-29T02:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
    when(plans.findReconciledBefore(eq(PlanState.RECONCILED), any(Instant.class)))
        .thenReturn(List.of());

    job(clock, 90).runOnce();

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(plans).findReconciledBefore(eq(PlanState.RECONCILED), cutoff.capture());
    assertThat(cutoff.getValue()).isEqualTo(fixedNow.minus(90, ChronoUnit.DAYS));
  }

  @Test
  void run_publishesSuccessCounter() {
    // run() wraps runOnce() in JobMetrics.timed -- a clean run increments the success counter
    // once. SRE alarms (absence + 100% failure rate) hinge on this counter firing reliably.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T02:00:00Z"), ZoneId.of("UTC"));
    when(plans.findReconciledBefore(eq(PlanState.RECONCILED), any(Instant.class)))
        .thenReturn(List.of());

    job(clock, 90).run();

    double successCount =
        meterRegistry
            .get("weekly_commit.scheduled.job.runs_total")
            .tag("job", "ArchivalJob")
            .tag("outcome", "success")
            .counter()
            .count();
    assertThat(successCount).isEqualTo(1.0);
  }
}
