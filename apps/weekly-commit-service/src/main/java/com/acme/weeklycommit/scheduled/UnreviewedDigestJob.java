package com.acme.weeklycommit.scheduled;

import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly Monday 09:00 UTC digest job. Finds RECONCILED plans whose manager has not acknowledged
 * within the threshold window (default 72h since {@code reconciledAt}) and groups them by
 * skip-level manager (the manager's manager) for a single digest dispatch per skip-level.
 *
 * <p>Per ADR-0002 the notification event type is {@code WEEKLY_UNREVIEWED_DIGEST}. The
 * notification-svc digest body contract is not yet built -- {@code NotificationClient} currently
 * only handles state-transition events. v1 of this job therefore computes the grouping (the
 * high-value, testable business logic) and logs each grouping at INFO; the actual {@code
 * sendDigest} HTTP call is deferred to a later scope and marked with a {@code TODO(group-11)} below
 * to avoid a forced-extension of {@code NotificationSender} / {@code ResilientNotificationSender}
 * before the contract is finalized.
 *
 * <p>Coordinated via {@link SchedulerLock}; only one pod fires per cron tick. A per-plan employee
 * lookup failure does <b>not</b> abort the batch -- the next week's tick will retry, and an
 * unreviewed plan that misses one digest will appear in the next.
 */
@Component
@ConditionalOnProperty(
    name = "weekly-commit.scheduled.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UnreviewedDigestJob {

  private static final Logger log = LoggerFactory.getLogger(UnreviewedDigestJob.class);

  private final WeeklyPlanRepository plans;
  private final EmployeeRepository employees;
  private final Clock clock;
  private final JobMetrics metrics;
  private final int thresholdHours;

  public UnreviewedDigestJob(
      WeeklyPlanRepository plans,
      EmployeeRepository employees,
      Clock clock,
      JobMetrics metrics,
      @Value("${weekly-commit.scheduled.unreviewed-threshold-hours:72}") int thresholdHours) {
    this.plans = plans;
    this.employees = employees;
    this.clock = clock;
    this.metrics = metrics;
    this.thresholdHours = thresholdHours;
  }

  @Scheduled(cron = "${weekly-commit.scheduled.unreviewed-digest-cron:0 0 9 * * MON}")
  @SchedulerLock(name = "UnreviewedDigestJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
  public void run() {
    metrics.timed(
        "UnreviewedDigestJob",
        () -> {
          DigestRunSummary summary = runOnce();
          log.info(
              "UnreviewedDigestJob: plans={} skipLevelGroups={} unmanaged={} noSkipLevel={}",
              summary.plansFound(),
              summary.skipLevelGroups(),
              summary.unmanagedCount(),
              summary.noSkipLevelCount());
        });
  }

  /**
   * Run a single pass. Returns a small summary record for assertions and structured logs. Caller-
   * driven entry point so unit tests can invoke without the {@code @Scheduled} proxy.
   */
  public DigestRunSummary runOnce() {
    Instant threshold = clock.instant().minus(thresholdHours, ChronoUnit.HOURS);
    List<WeeklyPlan> unreviewed =
        plans.findUnreviewedReconciledBefore(PlanState.RECONCILED, threshold);

    if (unreviewed.isEmpty()) {
      return new DigestRunSummary(0, 0, 0, 0);
    }

    // LinkedHashMap so log ordering is deterministic across pods/runs given the same input.
    Map<UUID, List<UUID>> bySkipLevel = new LinkedHashMap<>();
    List<UUID> unmanaged = new ArrayList<>();
    List<UUID> noSkipLevel = new ArrayList<>();

    for (WeeklyPlan plan : unreviewed) {
      UUID planId = plan.getId();
      UUID ownerId = plan.getEmployeeId();

      Optional<Employee> ownerOpt;
      try {
        ownerOpt = employees.findById(ownerId);
      } catch (RuntimeException e) {
        // Per-plan lookup failure must not abort the batch. Common cases: transient DB blip,
        // Auth0 sync row missing for a freshly-provisioned employee. The plan will appear
        // again next week if still unreviewed.
        log.warn(
            "UnreviewedDigestJob: failed to load owner employee={} for plan={} (skipping): {}",
            ownerId,
            planId,
            e.toString());
        continue;
      }

      if (ownerOpt.isEmpty()) {
        log.warn(
            "UnreviewedDigestJob: owner employee={} not found for plan={} (skipping)",
            ownerId,
            planId);
        continue;
      }

      UUID directManagerId = ownerOpt.get().getManagerId();
      if (directManagerId == null) {
        unmanaged.add(planId);
        continue;
      }

      Optional<Employee> managerOpt;
      try {
        managerOpt = employees.findById(directManagerId);
      } catch (RuntimeException e) {
        log.warn(
            "UnreviewedDigestJob: failed to load direct manager={} for plan={} (skipping): {}",
            directManagerId,
            planId,
            e.toString());
        continue;
      }

      if (managerOpt.isEmpty()) {
        log.warn(
            "UnreviewedDigestJob: direct manager={} not found for plan={} (skipping)",
            directManagerId,
            planId);
        continue;
      }

      UUID skipLevelId = managerOpt.get().getManagerId();
      if (skipLevelId == null) {
        noSkipLevel.add(planId);
        continue;
      }

      bySkipLevel.computeIfAbsent(skipLevelId, k -> new ArrayList<>()).add(planId);
    }

    if (!unmanaged.isEmpty()) {
      log.warn(
          "UnreviewedDigestJob: {} unmanaged plan(s) (owner has no manager) -- ids={}",
          unmanaged.size(),
          unmanaged);
    }
    if (!noSkipLevel.isEmpty()) {
      log.warn(
          "UnreviewedDigestJob: {} plan(s) whose manager has no skip-level -- ids={}",
          noSkipLevel.size(),
          noSkipLevel);
    }
    for (Map.Entry<UUID, List<UUID>> entry : bySkipLevel.entrySet()) {
      log.info(
          "UnreviewedDigestJob: skipLevelManager={} planCount={} planIds={}",
          entry.getKey(),
          entry.getValue().size(),
          entry.getValue());
      // TODO(group-11): dispatch WEEKLY_UNREVIEWED_DIGEST to notification-svc here, once the
      // digest body contract is finalized and NotificationClient grows a sendDigest(...) method.
      // Intentionally NOT extending NotificationSender / ResilientNotificationSender in this
      // scope -- adding a half-shaped method to those interfaces would force a noop in the
      // logging fallback and a no-contract HTTP call in the resilient path. See ADR-0002.
    }

    return new DigestRunSummary(
        unreviewed.size(), bySkipLevel.size(), unmanaged.size(), noSkipLevel.size());
  }

  /**
   * Summary of one pass. Returned by {@link #runOnce()} for unit-test assertions and emitted as a
   * structured log line by the scheduled {@link #run()} entry point.
   */
  public record DigestRunSummary(
      int plansFound, int skipLevelGroups, int unmanagedCount, int noSkipLevelCount) {}
}
