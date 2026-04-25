package com.acme.weeklycommit.scheduled;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import com.acme.weeklycommit.service.statemachine.WeeklyPlanStateMachine;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly job that locks DRAFT plans whose week-start cutoff has elapsed.
 *
 * <p>Cutoff: {@code now - autoLockCutoffHours} → the latest Monday whose Monday-00:00-UTC is at or
 * before that instant. Default 36h after week-start = Tuesday 12:00 UTC for a Monday-start week,
 * matching the user-flow comment in {@code application.yml}.
 *
 * <p>Coordinated via {@link SchedulerLock} so only one pod fires per cron tick. A single transition
 * failure (optimistic-lock conflict, RCDO outage, etc.) does <b>not</b> abort the remaining batch
 * -- the next hour's tick will retry only the still-DRAFT plans, so a transient fault costs at most
 * one hour of latency on those plans.
 */
@Component
@ConditionalOnProperty(
    name = "weekly-commit.scheduled.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AutoLockJob {

  private static final Logger log = LoggerFactory.getLogger(AutoLockJob.class);

  private final WeeklyPlanRepository plans;
  private final WeeklyPlanStateMachine stateMachine;
  private final Clock clock;
  private final JobMetrics metrics;
  private final int cutoffHours;

  public AutoLockJob(
      WeeklyPlanRepository plans,
      WeeklyPlanStateMachine stateMachine,
      Clock clock,
      JobMetrics metrics,
      @Value("${weekly-commit.scheduled.auto-lock-cutoff-hours-after-week-start:36}")
          int cutoffHours) {
    this.plans = plans;
    this.stateMachine = stateMachine;
    this.clock = clock;
    this.metrics = metrics;
    this.cutoffHours = cutoffHours;
  }

  @Scheduled(cron = "${weekly-commit.scheduled.auto-lock-cron:0 0 * * * *}")
  @SchedulerLock(name = "AutoLockJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
  public void run() {
    metrics.timed(
        "AutoLockJob",
        () -> {
          int locked = runOnce();
          log.info("AutoLockJob: locked {} plans past cutoff", locked);
        });
  }

  /**
   * Run a single pass. Returns the number of plans successfully transitioned. Caller-driven entry
   * point so unit tests can invoke without the {@code @Scheduled} proxy.
   */
  public int runOnce() {
    LocalDate cutoffWeekStart = computeCutoffWeekStart();
    List<WeeklyPlan> drafts = plans.findDraftsPastCutoff(PlanState.DRAFT, cutoffWeekStart);
    int locked = 0;
    for (WeeklyPlan plan : drafts) {
      try {
        stateMachine.transition(plan.getId(), PlanState.LOCKED, null);
        locked++;
      } catch (RuntimeException e) {
        // Per-plan failure must not abort the batch. Common cases: optimistic-lock conflict
        // when the IC just locked manually, RCDO outage during the post-commit notification,
        // or a state-machine guard rejecting a state that flipped under us.
        log.warn(
            "AutoLockJob: failed to lock plan={} (will retry next tick): {}",
            plan.getId(),
            e.toString());
      }
    }
    return locked;
  }

  private LocalDate computeCutoffWeekStart() {
    Instant cutoffInstant = clock.instant().minusSeconds((long) cutoffHours * 3600);
    LocalDate cutoffDate = cutoffInstant.atZone(ZoneOffset.UTC).toLocalDate();
    // The latest Monday on/before cutoffDate. previousOrSame so a cutoff that lands on a Monday
    // returns that Monday (not the prior week).
    return cutoffDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }
}
