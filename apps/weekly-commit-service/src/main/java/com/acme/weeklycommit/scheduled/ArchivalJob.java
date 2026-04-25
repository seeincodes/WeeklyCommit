package com.acme.weeklycommit.scheduled;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import com.acme.weeklycommit.service.statemachine.WeeklyPlanStateMachine;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job that archives RECONCILED plans whose {@code reconciledAt} is older than the
 * configured threshold (default 90 days). ARCHIVED is terminal; the row stays in {@code
 * weekly_plan} -- v1 has no row deletion.
 *
 * <p>Cutoff: {@code clock.instant() - olderThanDays} computed in the application layer (never NOW()
 * in SQL, per MEMO week-math decision). Coordinated via {@link SchedulerLock} so only one pod runs
 * the batch per cron tick. A single transition failure (optimistic-lock conflict, audit write
 * fault, etc.) does <b>not</b> abort the batch -- the next nightly tick will retry only the
 * still-RECONCILED plans, so a transient fault costs at most 24h of latency on those plans.
 */
@Component
@ConditionalOnProperty(
    name = "weekly-commit.scheduled.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ArchivalJob {

  private static final Logger log = LoggerFactory.getLogger(ArchivalJob.class);

  private final WeeklyPlanRepository plans;
  private final WeeklyPlanStateMachine stateMachine;
  private final Clock clock;
  private final JobMetrics metrics;
  private final int olderThanDays;

  public ArchivalJob(
      WeeklyPlanRepository plans,
      WeeklyPlanStateMachine stateMachine,
      Clock clock,
      JobMetrics metrics,
      @Value("${weekly-commit.scheduled.archival-older-than-days:90}") int olderThanDays) {
    this.plans = plans;
    this.stateMachine = stateMachine;
    this.clock = clock;
    this.metrics = metrics;
    this.olderThanDays = olderThanDays;
  }

  @Scheduled(cron = "${weekly-commit.scheduled.archival-cron:0 0 2 * * *}")
  @SchedulerLock(name = "ArchivalJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
  public void run() {
    metrics.timed(
        "ArchivalJob",
        () -> {
          int archived = runOnce();
          log.info("ArchivalJob: archived {} plans older than {} days", archived, olderThanDays);
        });
  }

  /**
   * Run a single pass. Returns the number of plans successfully transitioned. Caller-driven entry
   * point so unit tests can invoke without the {@code @Scheduled} proxy.
   */
  public int runOnce() {
    Instant before = clock.instant().minus(olderThanDays, ChronoUnit.DAYS);
    List<WeeklyPlan> reconciled = plans.findReconciledBefore(PlanState.RECONCILED, before);
    int archived = 0;
    for (WeeklyPlan plan : reconciled) {
      try {
        stateMachine.transition(plan.getId(), PlanState.ARCHIVED, null);
        archived++;
      } catch (RuntimeException e) {
        // Per-plan failure must not abort the batch. Common cases: optimistic-lock conflict if
        // the row was touched concurrently, or a transient audit/notification write fault. The
        // next nightly tick will pick the plan up again so long as it remains RECONCILED past
        // threshold.
        log.warn(
            "ArchivalJob: failed to archive plan={} (will retry next tick): {}",
            plan.getId(),
            e.toString());
      }
    }
    return archived;
  }
}
