package com.acme.weeklycommit.service;

import com.acme.weeklycommit.api.dto.RollupResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.ActualStatus;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates a week's worth of plans for a manager into the dashboard payload (USER_FLOW.md Manager
 * Review). Computes alignment / completion percentages, tier distribution, unreviewed plan count,
 * stuck commit count, and per-member cards with the standard flag set.
 *
 * <p><b>Performance:</b> v1 implementation walks plans → commits → derived per member. At our scale
 * (≤50 reports, ≤10 commits/plan, carry-streak cap 52) the worst case is roughly {@code 50 × 10 ×
 * 52 = 26000} repo calls per request. The CTE rewrite documented on {@link
 * DerivedFieldService#carryStreak} addresses this when the rollup P95 target regresses.
 *
 * <p><b>Authz:</b> caller must be the queried managerId, or hold the {@code ADMIN} role (skip-level
 * / ops). Same rule as {@link WeeklyPlanService#findTeamPlans}.
 */
@Service
public class RollupService {

  /** Reflection preview length in MemberCard (~80 chars per USER_FLOW.md). */
  static final int REFLECTION_PREVIEW_CHARS = 80;

  /** Hours after reconciledAt to consider a plan "unreviewed" (presearch / USER_FLOW.md). */
  static final long UNREVIEWED_THRESHOLD_HOURS = 72;

  /** Hard page size for the underlying team query. ≤50 direct reports per manager (PRD). */
  private static final int TEAM_FETCH_PAGE_SIZE = 100;

  private final WeeklyPlanRepository plans;
  private final WeeklyCommitRepository commits;
  private final EmployeeRepository employees;
  private final DerivedFieldService derivedFieldService;
  private final Clock clock;

  public RollupService(
      WeeklyPlanRepository plans,
      WeeklyCommitRepository commits,
      EmployeeRepository employees,
      DerivedFieldService derivedFieldService,
      Clock clock) {
    this.plans = plans;
    this.commits = commits;
    this.employees = employees;
    this.derivedFieldService = derivedFieldService;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public RollupResponse computeRollup(
      UUID managerId, LocalDate weekStart, AuthenticatedPrincipal caller) {
    requireOwnTeamOrAdmin(managerId, caller);

    List<WeeklyPlan> teamPlans =
        plans
            .findTeamPlans(managerId, weekStart, PageRequest.of(0, TEAM_FETCH_PAGE_SIZE))
            .getContent();
    if (teamPlans.isEmpty()) {
      return emptyRollup();
    }

    Map<UUID, String> employeeNames = loadEmployeeNames(teamPlans);
    Instant now = Instant.now(clock);

    int totalCommits = 0;
    int doneCommits = 0;
    int plansWithAtLeastOneRock = 0;
    int unreviewedCount = 0;
    int stuckCommitCount = 0;
    Map<String, Integer> tierDistribution = new LinkedHashMap<>();
    tierDistribution.put(ChessTier.ROCK.name(), 0);
    tierDistribution.put(ChessTier.PEBBLE.name(), 0);
    tierDistribution.put(ChessTier.SAND.name(), 0);

    List<RollupResponse.MemberCard> members = new ArrayList<>(teamPlans.size());

    for (WeeklyPlan plan : teamPlans) {
      List<WeeklyCommit> planCommits = commits.findByPlanIdOrderByDisplayOrderAsc(plan.getId());

      Map<String, Integer> tierCounts = new LinkedHashMap<>();
      tierCounts.put(ChessTier.ROCK.name(), 0);
      tierCounts.put(ChessTier.PEBBLE.name(), 0);
      tierCounts.put(ChessTier.SAND.name(), 0);
      boolean planHasStuck = false;
      RollupResponse.TopRockSummary topRock = null;
      int topRockOrder = Integer.MAX_VALUE;

      for (WeeklyCommit c : planCommits) {
        totalCommits++;
        if (c.getActualStatus() == ActualStatus.DONE) {
          doneCommits++;
        }
        String tierKey = c.getChessTier().name();
        tierCounts.merge(tierKey, 1, Integer::sum);
        tierDistribution.merge(tierKey, 1, Integer::sum);

        DerivedFieldService.Derived derived = derivedFieldService.deriveFor(c.getId());
        if (derived.stuckFlag()) {
          stuckCommitCount++;
          planHasStuck = true;
        }
        if (c.getChessTier() == ChessTier.ROCK && c.getDisplayOrder() < topRockOrder) {
          topRockOrder = c.getDisplayOrder();
          topRock = new RollupResponse.TopRockSummary(c.getId(), c.getTitle());
        }
      }

      boolean planHasRock = tierCounts.getOrDefault(ChessTier.ROCK.name(), 0) > 0;
      if (planHasRock) {
        plansWithAtLeastOneRock++;
      }

      List<String> flags = computeFlags(plan, planCommits, planHasStuck, planHasRock, now);
      if (flags.contains("UNREVIEWED_72H")) {
        unreviewedCount++;
      }

      members.add(
          new RollupResponse.MemberCard(
              plan.getEmployeeId(),
              employeeNames.getOrDefault(plan.getEmployeeId(), ""),
              plan.getState(),
              topRock,
              tierCounts,
              previewOf(plan.getReflectionNote()),
              flags));
    }

    members.sort(RollupService::flaggedFirst);

    return new RollupResponse(
        ratio(plansWithAtLeastOneRock, teamPlans.size()),
        ratio(doneCommits, totalCommits),
        tierDistribution,
        unreviewedCount,
        stuckCommitCount,
        members);
  }

  private Map<UUID, String> loadEmployeeNames(List<WeeklyPlan> teamPlans) {
    List<UUID> ids = teamPlans.stream().map(WeeklyPlan::getEmployeeId).distinct().toList();
    Map<UUID, String> names = new HashMap<>();
    for (Employee e : employees.findAllById(ids)) {
      names.put(e.getId(), e.getDisplayName() == null ? "" : e.getDisplayName());
    }
    return names;
  }

  private List<String> computeFlags(
      WeeklyPlan plan,
      List<WeeklyCommit> planCommits,
      boolean planHasStuck,
      boolean planHasRock,
      Instant now) {
    List<String> flags = new ArrayList<>();
    if (plan.getState() == PlanState.RECONCILED
        && plan.getManagerReviewedAt() == null
        && plan.getReconciledAt() != null
        && Duration.between(plan.getReconciledAt(), now).toHours() >= UNREVIEWED_THRESHOLD_HOURS) {
      flags.add("UNREVIEWED_72H");
    }
    if (plan.getState() == PlanState.DRAFT && planCommits.isEmpty()) {
      flags.add("DRAFT_WITH_UNLINKED");
    }
    if (planHasStuck) {
      flags.add("STUCK_COMMIT");
    }
    if (!planHasRock && plan.getState() != PlanState.DRAFT) {
      flags.add("NO_TOP_ROCK");
    }
    return flags;
  }

  /** Members with at least one flag sort before un-flagged members. Stable for ties. */
  private static int flaggedFirst(RollupResponse.MemberCard a, RollupResponse.MemberCard b) {
    int aWeight = a.flags().isEmpty() ? 1 : 0;
    int bWeight = b.flags().isEmpty() ? 1 : 0;
    return Integer.compare(aWeight, bWeight);
  }

  private void requireOwnTeamOrAdmin(UUID managerId, AuthenticatedPrincipal caller) {
    boolean isOwnTeam = caller.employeeId().equals(managerId);
    boolean isAdmin = caller.hasRole("ADMIN");
    if (!isOwnTeam && !isAdmin) {
      throw new AccessDeniedException(
          "caller " + caller.employeeId() + " cannot read rollup for manager " + managerId);
    }
  }

  private static String previewOf(String note) {
    if (note == null || note.isEmpty()) {
      return "";
    }
    return note.length() <= REFLECTION_PREVIEW_CHARS
        ? note
        : note.substring(0, REFLECTION_PREVIEW_CHARS);
  }

  private static BigDecimal ratio(int numerator, int denominator) {
    if (denominator == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
  }

  private RollupResponse emptyRollup() {
    Map<String, Integer> emptyTiers = new LinkedHashMap<>();
    emptyTiers.put(ChessTier.ROCK.name(), 0);
    emptyTiers.put(ChessTier.PEBBLE.name(), 0);
    emptyTiers.put(ChessTier.SAND.name(), 0);
    return new RollupResponse(BigDecimal.ZERO, BigDecimal.ZERO, emptyTiers, 0, 0, List.of());
  }
}
