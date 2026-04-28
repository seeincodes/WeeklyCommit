package com.acme.weeklycommit.perf;

import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds enough plans + commits at startup that the k6 perf harness has realistic row counts to
 * exercise the {@code GET /plans/me/current}, {@code GET /plans/team}, and {@code GET /rollup/team}
 * hot paths under load. Without this, the perf workflow would be measuring p95 on a single-row
 * query, which says nothing useful about production behavior.
 *
 * <p>Profile-gated. The {@code perf} Spring profile is activated only by:
 *
 * <ul>
 *   <li>{@code docker-compose.e2e.yml} when the perf workflow brings up the stack
 *   <li>local {@code SPRING_PROFILES_ACTIVE=e2e,perf} for hand-running the k6 script
 * </ul>
 *
 * <p>It is <strong>never</strong> active in production. Any of test, dev, prod, or demo deployments
 * leave this bean uninstantiated.
 *
 * <p>Idempotent: re-running the seeder against an already-seeded DB is a no-op (checks for the
 * fixed manager UUID before inserting). This matters because compose restarts re-run startup; a
 * second insert pass would violate the {@code uq_weekly_plan_employee_week} unique constraint and
 * crash the boot.
 *
 * <p>Seed shape:
 *
 * <ul>
 *   <li>1 manager ({@link #PERF_MANAGER_ID}) — JWT subject for the k6 script targeting team
 *       endpoints
 *   <li>1 IC ({@link #PERF_IC_ID}) reporting to that manager — JWT subject for the k6 script
 *       targeting {@code /plans/me/current}
 *   <li>49 additional ICs reporting to the same manager (so the team rollup has 50 reports)
 *   <li>1 {@code WeeklyPlan} per IC for "this Monday" UTC = 50 plans
 *   <li>5 {@code WeeklyCommit}s per plan = 250 commits total
 * </ul>
 *
 * <p>50 reports is the manager-rollup payload size called out in PRD §Performance Targets.
 */
@Component
@Profile("perf")
public class PerfDataSeeder {

  private static final Logger log = LoggerFactory.getLogger(PerfDataSeeder.class);

  /** Stable subject UUID for the manager seeded here; the k6 JWT signer reuses this. */
  public static final UUID PERF_MANAGER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");

  /** Stable subject UUID for the IC the k6 GET /plans/me/current script logs in as. */
  public static final UUID PERF_IC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  /** Stable org id; matches what the k6 JWT signer encodes. */
  public static final UUID PERF_ORG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  /** Number of ICs (incl. PERF_IC_ID) reporting to PERF_MANAGER_ID. */
  private static final int IC_COUNT = 50;

  /** Number of commits per plan. */
  private static final int COMMITS_PER_PLAN = 5;

  /**
   * Stable Supporting Outcome UUID that every seeded commit links to. The RcdoClient is mocked /
   * stubbed in the perf-profile environment (the rcdo-stub in docker-compose.e2e.yml returns a
   * canned response), so we don't need a corresponding row in any RCDO table — the FK is to an
   * external system, not to a local entity.
   */
  private static final UUID PERF_SUPPORTING_OUTCOME_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  private final EmployeeRepository employees;
  private final WeeklyPlanRepository plans;
  private final WeeklyCommitRepository commits;

  public PerfDataSeeder(
      EmployeeRepository employees, WeeklyPlanRepository plans, WeeklyCommitRepository commits) {
    this.employees = employees;
    this.plans = plans;
    this.commits = commits;
  }

  @PostConstruct
  @Transactional
  public void seed() {
    if (employees.existsById(PERF_MANAGER_ID)) {
      log.info("[perf-seeder] DB already seeded (manager {} present); skipping.", PERF_MANAGER_ID);
      return;
    }

    log.info(
        "[perf-seeder] seeding {} ICs + {} plans + {} commits...",
        IC_COUNT,
        IC_COUNT,
        IC_COUNT * COMMITS_PER_PLAN);

    Employee manager = new Employee(PERF_MANAGER_ID, PERF_ORG_ID);
    manager.setDisplayName("Perf Manager");
    employees.save(manager);

    LocalDate weekStart =
        LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    List<Employee> ics = new ArrayList<>(IC_COUNT);
    List<WeeklyPlan> seededPlans = new ArrayList<>(IC_COUNT);
    List<WeeklyCommit> seededCommits = new ArrayList<>(IC_COUNT * COMMITS_PER_PLAN);

    for (int i = 0; i < IC_COUNT; i++) {
      UUID employeeId = (i == 0) ? PERF_IC_ID : UUID.randomUUID();
      Employee ic = new Employee(employeeId, PERF_ORG_ID);
      ic.setManagerId(PERF_MANAGER_ID);
      ic.setDisplayName(String.format("Perf IC %02d", i));
      ics.add(ic);

      UUID planId = UUID.randomUUID();
      WeeklyPlan plan = new WeeklyPlan(planId, employeeId, weekStart);
      seededPlans.add(plan);

      for (int j = 0; j < COMMITS_PER_PLAN; j++) {
        // Tier rotation gives the rollup query a non-trivial tier-distribution
        // shape to compute (vs. all-Pebble being suspiciously uniform).
        ChessTier tier =
            switch (j % 3) {
              case 0 -> ChessTier.ROCK;
              case 1 -> ChessTier.PEBBLE;
              default -> ChessTier.SAND;
            };
        WeeklyCommit commit =
            new WeeklyCommit(
                UUID.randomUUID(),
                planId,
                String.format("Perf commit %02d-%d", i, j),
                PERF_SUPPORTING_OUTCOME_ID,
                tier,
                j);
        seededCommits.add(commit);
      }
    }

    employees.saveAll(ics);
    plans.saveAll(seededPlans);
    commits.saveAll(seededCommits);

    log.info(
        "[perf-seeder] done. manager={}, ic_seed={}, plans={}, commits={}, week_of={}",
        PERF_MANAGER_ID,
        PERF_IC_ID,
        seededPlans.size(),
        seededCommits.size(),
        weekStart);
  }
}
