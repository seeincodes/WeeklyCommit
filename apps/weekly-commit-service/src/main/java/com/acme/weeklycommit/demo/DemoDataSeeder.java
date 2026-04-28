package com.acme.weeklycommit.demo;

import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.ActualStatus;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the demo deploy's empty Postgres on first boot. Idempotent: detects already-seeded state by
 * checking for the canonical manager UUID and skips on subsequent boots.
 *
 * <p>Profile-gated to {@code demo}. Production runs without this profile and never instantiates the
 * bean.
 *
 * <p>What gets seeded:
 *
 * <ul>
 *   <li>One manager (Ada Lovelace) whose UUID matches the {@code MANAGER} test user the frontend
 *       devAuth shim signs JWTs for by default.
 *   <li>Three ICs (Ben Carter, Cleo Davis, Dax Evans) reporting to Ada. Ben's UUID matches the
 *       {@code IC} test user; Cleo and Dax get fresh UUIDs.
 *   <li>One unassigned IC (Frankie Hopper) matching the {@code IC_NULL_MANAGER} test user, so the
 *       admin-unassigned-employees endpoint has data.
 *   <li>For each of the four ICs (Ada herself doesn't get plans -- she's the manager surface): a
 *       current-week DRAFT plan with 2-3 commits across tiers, plus a prior-week RECONCILED plan
 *       with realistic completion outcomes so the manager rollup has history to render.
 * </ul>
 *
 * <p>UUIDs match {@code apps/weekly-commit-ui/cypress/support/auth/testUsers.ts}: the frontend
 * mints JWTs for these subjects, the seeder writes employee rows for the same subjects, and the
 * rollup query joining JWT-derived {@code managerId} against {@code employee.manager_id} resolves
 * to a populated team.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  // Canonical UUIDs from apps/weekly-commit-ui/cypress/support/auth/testUsers.ts.
  // Drift here means the frontend's JWT `sub` won't match a seeded employee row,
  // and every authenticated request will look like an unknown user.
  static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  static final UUID MANAGER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  static final UUID IC_BEN = UUID.fromString("44444444-4444-4444-4444-444444444444");
  static final UUID IC_FRANKIE_UNASSIGNED = UUID.fromString("55555555-5555-5555-5555-555555555555");

  // Two reports we own here (not in testUsers.ts because the cypress suite doesn't
  // need them). Keep the prefix `66...` / `77...` so manual cross-checking against
  // the demo data is unambiguous.
  static final UUID IC_CLEO = UUID.fromString("66666666-6666-6666-6666-666666666666");
  static final UUID IC_DAX = UUID.fromString("77777777-7777-7777-7777-777777777777");

  // Hardcoded outcome ids mirroring StubRcdoController's catalog. Choosing them
  // here (rather than randomizing) means the picker and the seeded data line up
  // visually: the user clicks into a draft commit, sees "Alignment tooling GA"
  // selected, and the picker dropdown shows the same label.
  private static final UUID OUTCOME_ALIGNMENT =
      UUID.fromString("aaaaaaa1-1111-1111-1111-aaaaaaaaaaaa");
  private static final UUID OUTCOME_SELF_SERVE =
      UUID.fromString("aaaaaaa2-1111-1111-1111-aaaaaaaaaaaa");
  private static final UUID OUTCOME_ONBOARDING_AB =
      UUID.fromString("aaaaaaa3-1111-1111-1111-aaaaaaaaaaaa");
  private static final UUID OUTCOME_HIRING =
      UUID.fromString("bbbbbbb1-2222-2222-2222-bbbbbbbbbbbb");
  private static final UUID OUTCOME_ONBOARDING_KIT =
      UUID.fromString("bbbbbbb2-2222-2222-2222-bbbbbbbbbbbb");

  private final EmployeeRepository employees;
  private final WeeklyPlanRepository plans;
  private final WeeklyCommitRepository commits;
  private final Clock clock;
  private final boolean seedEnabled;

  public DemoDataSeeder(
      EmployeeRepository employees,
      WeeklyPlanRepository plans,
      WeeklyCommitRepository commits,
      Clock clock,
      @Value("${weekly-commit.demo.seed:true}") boolean seedEnabled) {
    this.employees = employees;
    this.plans = plans;
    this.commits = commits;
    this.clock = clock;
    this.seedEnabled = seedEnabled;
  }

  @Override
  @Transactional
  public void run(org.springframework.boot.ApplicationArguments args) {
    if (!seedEnabled) {
      log.info("[demo-seed] DEMO_SEED=false — skipping demo data seed");
      return;
    }
    if (employees.findById(MANAGER_ID).isPresent()) {
      log.info("[demo-seed] manager {} already present — skipping seed", MANAGER_ID);
      return;
    }
    log.info("[demo-seed] empty demo DB — seeding employees + plans + commits");

    seedEmployees();
    LocalDate currentWeek = currentWeekStart();
    LocalDate priorWeek = currentWeek.minusWeeks(1);

    seedCurrentWeekDrafts(currentWeek);
    seedPriorWeekReconciled(priorWeek);

    log.info("[demo-seed] done — manager={}, currentWeek={}", MANAGER_ID, currentWeek);
  }

  private void seedEmployees() {
    employees.save(employee(MANAGER_ID, "Ada Lovelace", null));
    employees.save(employee(ADMIN_ID, "Site Admin", null));
    employees.save(employee(IC_BEN, "Ben Carter", MANAGER_ID));
    employees.save(employee(IC_CLEO, "Cleo Davis", MANAGER_ID));
    employees.save(employee(IC_DAX, "Dax Evans", MANAGER_ID));
    employees.save(employee(IC_FRANKIE_UNASSIGNED, "Frankie Hopper", null));
  }

  private Employee employee(UUID id, String displayName, UUID managerId) {
    Employee e = new Employee(id, ORG_ID);
    e.setDisplayName(displayName);
    e.setManagerId(managerId);
    e.setActive(true);
    e.setLastSyncedAt(Instant.now(clock));
    return e;
  }

  private void seedCurrentWeekDrafts(LocalDate weekStart) {
    // Ada (the manager) gets her own plan too -- managers are also ICs.
    seedPlanWithCommits(
        MANAGER_ID,
        weekStart,
        PlanState.DRAFT,
        null,
        List.of(
            new CommitSeed("Approve Q3 hiring slate", OUTCOME_HIRING, ChessTier.ROCK, 0),
            new CommitSeed("1:1s with all reports", OUTCOME_ONBOARDING_KIT, ChessTier.PEBBLE, 1),
            new CommitSeed("Review picker spec PR", OUTCOME_ALIGNMENT, ChessTier.PEBBLE, 2)));

    seedPlanWithCommits(
        IC_BEN,
        weekStart,
        PlanState.DRAFT,
        null,
        List.of(
            new CommitSeed("Land RCDO picker integration", OUTCOME_ALIGNMENT, ChessTier.ROCK, 0),
            new CommitSeed("Self-serve activation A/B", OUTCOME_SELF_SERVE, ChessTier.PEBBLE, 1),
            new CommitSeed("Triage support tickets", OUTCOME_ONBOARDING_AB, ChessTier.SAND, 2)));

    seedPlanWithCommits(
        IC_CLEO,
        weekStart,
        PlanState.DRAFT,
        null,
        List.of(
            new CommitSeed(
                "Ship reflection note 480-char warning", OUTCOME_SELF_SERVE, ChessTier.ROCK, 0),
            new CommitSeed(
                "Pair with Dax on onboarding email", OUTCOME_ONBOARDING_KIT, ChessTier.PEBBLE, 1)));

    // Dax has no Rock yet — surfaces as a NO_TOP_ROCK flag in the manager rollup.
    seedPlanWithCommits(
        IC_DAX,
        weekStart,
        PlanState.DRAFT,
        null,
        List.of(
            new CommitSeed(
                "Refresh new-hire onboarding kit", OUTCOME_ONBOARDING_KIT, ChessTier.PEBBLE, 0),
            new CommitSeed(
                "Triage open backlog issues", OUTCOME_ONBOARDING_AB, ChessTier.SAND, 1)));
  }

  private void seedPriorWeekReconciled(LocalDate priorWeek) {
    Instant reconciledAt = Instant.now(clock).minusSeconds(2L * 24 * 3600);
    Instant lockedAt = reconciledAt.minusSeconds(2L * 24 * 3600);

    // Ben: clean prior week, all DONE, manager-reviewed -> no flags on manager
    // rollup, will reorder behind flagged members.
    seedPlanWithReconciledCommits(
        IC_BEN,
        priorWeek,
        lockedAt,
        reconciledAt,
        reconciledAt.plusSeconds(3600),
        "Picker spike landed; A/B running smooth.",
        List.of(
            new ReconciledCommitSeed(
                "RCDO picker spike", OUTCOME_ALIGNMENT, ChessTier.ROCK, 0, ActualStatus.DONE),
            new ReconciledCommitSeed(
                "Activation funnel telemetry",
                OUTCOME_SELF_SERVE,
                ChessTier.PEBBLE,
                1,
                ActualStatus.DONE)));

    // Cleo: reconciled but UN-reviewed for >72h -> UNREVIEWED_72H flag.
    seedPlanWithReconciledCommits(
        IC_CLEO,
        priorWeek,
        lockedAt,
        reconciledAt.minusSeconds(2L * 24 * 3600), // reconciled 4 days ago
        null, // no manager review
        "Spent more time than expected on the migration. Carrying onboarding doc.",
        List.of(
            new ReconciledCommitSeed(
                "Migrate auth helper", OUTCOME_ALIGNMENT, ChessTier.ROCK, 0, ActualStatus.DONE),
            new ReconciledCommitSeed(
                "Onboarding doc draft",
                OUTCOME_ONBOARDING_KIT,
                ChessTier.PEBBLE,
                1,
                ActualStatus.PARTIAL)));

    // Dax: reconciled with one MISSED commit -> the carry-forward affordance is
    // active in his current draft (we don't actually mint a carry pair here --
    // that's a UI flow the IC kicks off explicitly per [MVP6]). Still surfaces
    // partial/missed visibility on the manager card.
    seedPlanWithReconciledCommits(
        IC_DAX,
        priorWeek,
        lockedAt,
        reconciledAt,
        reconciledAt.plusSeconds(3600),
        "Got blocked on the onboarding kit decision; need direction.",
        List.of(
            new ReconciledCommitSeed(
                "Vendor selection memo", OUTCOME_HIRING, ChessTier.PEBBLE, 0, ActualStatus.MISSED),
            new ReconciledCommitSeed(
                "Sprint review prep",
                OUTCOME_ONBOARDING_KIT,
                ChessTier.SAND,
                1,
                ActualStatus.DONE)));
  }

  private void seedPlanWithCommits(
      UUID employeeId,
      LocalDate weekStart,
      PlanState state,
      String reflectionNote,
      List<CommitSeed> seeds) {
    WeeklyPlan plan = new WeeklyPlan(UUID.randomUUID(), employeeId, weekStart);
    plan.setState(state);
    if (reflectionNote != null) {
      plan.setReflectionNote(reflectionNote);
    }
    plans.save(plan);

    for (CommitSeed s : seeds) {
      WeeklyCommit c =
          new WeeklyCommit(
              UUID.randomUUID(), plan.getId(), s.title, s.outcomeId, s.tier, s.displayOrder);
      commits.save(c);
    }
  }

  private void seedPlanWithReconciledCommits(
      UUID employeeId,
      LocalDate weekStart,
      Instant lockedAt,
      Instant reconciledAt,
      Instant managerReviewedAt,
      String reflectionNote,
      List<ReconciledCommitSeed> seeds) {
    WeeklyPlan plan = new WeeklyPlan(UUID.randomUUID(), employeeId, weekStart);
    plan.setState(PlanState.RECONCILED);
    plan.setLockedAt(lockedAt);
    plan.setReconciledAt(reconciledAt);
    plan.setManagerReviewedAt(managerReviewedAt);
    plan.setReflectionNote(reflectionNote);
    plans.save(plan);

    for (ReconciledCommitSeed s : seeds) {
      WeeklyCommit c =
          new WeeklyCommit(
              UUID.randomUUID(), plan.getId(), s.title, s.outcomeId, s.tier, s.displayOrder);
      c.setActualStatus(s.status);
      commits.save(c);
    }
  }

  private LocalDate currentWeekStart() {
    return LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private record CommitSeed(String title, UUID outcomeId, ChessTier tier, int displayOrder) {}

  private record ReconciledCommitSeed(
      String title, UUID outcomeId, ChessTier tier, int displayOrder, ActualStatus status) {}
}
