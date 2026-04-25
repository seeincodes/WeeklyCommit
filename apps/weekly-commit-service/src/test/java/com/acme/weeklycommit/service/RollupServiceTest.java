package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class RollupServiceTest {

  private static final Instant FROZEN_NOW = Instant.parse("2026-05-08T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(FROZEN_NOW, ZoneId.of("UTC"));
  private static final LocalDate WEEK = LocalDate.parse("2026-04-27");

  @Mock private WeeklyPlanRepository plans;
  @Mock private WeeklyCommitRepository commits;
  @Mock private EmployeeRepository employees;
  @Mock private DerivedFieldService derivedFieldService;

  private RollupService service() {
    return new RollupService(plans, commits, employees, derivedFieldService, CLOCK);
  }

  @Test
  void rollup_managerOwnTeam_aggregatesTiersCompletionFlags() {
    UUID managerId = UUID.randomUUID();
    UUID emp1 = UUID.randomUUID();
    UUID emp2 = UUID.randomUUID();

    // emp1: RECONCILED 4 days ago, no manager review -> UNREVIEWED_72H flag.
    //       2 commits (1 ROCK, 1 PEBBLE), both DONE. No stuck. Has Rock -> no NO_TOP_ROCK.
    WeeklyPlan plan1 = new WeeklyPlan(UUID.randomUUID(), emp1, WEEK);
    plan1.setState(PlanState.RECONCILED);
    plan1.setReconciledAt(FROZEN_NOW.minus(96, ChronoUnit.HOURS));
    plan1.setReflectionNote("solid week, picker shipped");
    WeeklyCommit p1c1 =
        new WeeklyCommit(
            UUID.randomUUID(), plan1.getId(), "rock", UUID.randomUUID(), ChessTier.ROCK, 0);
    p1c1.setActualStatus(ActualStatus.DONE);
    WeeklyCommit p1c2 =
        new WeeklyCommit(
            UUID.randomUUID(), plan1.getId(), "pebble", UUID.randomUUID(), ChessTier.PEBBLE, 1);
    p1c2.setActualStatus(ActualStatus.DONE);

    // emp2: DRAFT plan with no commits -> DRAFT_WITH_UNLINKED flag.
    WeeklyPlan plan2 = new WeeklyPlan(UUID.randomUUID(), emp2, WEEK);
    // state DRAFT (default)

    when(plans.findTeamPlans(eq(managerId), eq(WEEK), any(Pageable.class)))
        .thenReturn((Page<WeeklyPlan>) new PageImpl<>(List.of(plan1, plan2)));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(plan1.getId())).thenReturn(List.of(p1c1, p1c2));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(plan2.getId())).thenReturn(List.of());
    Employee e1 = new Employee(emp1, UUID.randomUUID());
    e1.setDisplayName("Ada");
    Employee e2 = new Employee(emp2, UUID.randomUUID());
    e2.setDisplayName("Babs");
    when(employees.findAllById(any())).thenReturn(List.of(e1, e2));
    lenient()
        .when(derivedFieldService.deriveFor(any()))
        .thenReturn(new DerivedFieldService.Derived(1, false));

    RollupResponse r = service().computeRollup(managerId, WEEK, managerPrincipal(managerId));

    assertThat(r.tierDistribution())
        .containsEntry("ROCK", 1)
        .containsEntry("PEBBLE", 1)
        .containsEntry("SAND", 0);
    // 2 of 2 commits done -> 1.0000
    assertThat(r.completionPct()).isEqualByComparingTo(new BigDecimal("1.0000"));
    // 1 of 2 plans has at least one Rock -> 0.5000
    assertThat(r.alignmentPct()).isEqualByComparingTo(new BigDecimal("0.5000"));
    assertThat(r.unreviewedCount()).isEqualTo(1);
    assertThat(r.stuckCommitCount()).isZero();
    assertThat(r.members()).hasSize(2);

    // Both members are flagged; relative order between two flagged members is stable insertion.
    RollupResponse.MemberCard adaCard =
        r.members().stream().filter(m -> m.employeeId().equals(emp1)).findFirst().orElseThrow();
    assertThat(adaCard.flags()).contains("UNREVIEWED_72H");
    assertThat(adaCard.name()).isEqualTo("Ada");
    assertThat(adaCard.topRock()).isNotNull();
    assertThat(adaCard.topRock().title()).isEqualTo("rock");
    assertThat(adaCard.reflectionPreview()).isEqualTo("solid week, picker shipped");

    RollupResponse.MemberCard babsCard =
        r.members().stream().filter(m -> m.employeeId().equals(emp2)).findFirst().orElseThrow();
    assertThat(babsCard.flags()).contains("DRAFT_WITH_UNLINKED");
    assertThat(babsCard.topRock()).isNull();
  }

  @Test
  void rollup_caller_isPeerManager_throwsAccessDenied() {
    UUID otherManagerId = UUID.randomUUID();
    AuthenticatedPrincipal caller = managerPrincipal(UUID.randomUUID());

    assertThatThrownBy(() -> service().computeRollup(otherManagerId, WEEK, caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void rollup_admin_canQueryAnyManager() {
    UUID someManagerId = UUID.randomUUID();
    when(plans.findTeamPlans(eq(someManagerId), eq(WEEK), any(Pageable.class)))
        .thenReturn((Page<WeeklyPlan>) new PageImpl<>(List.<WeeklyPlan>of()));

    RollupResponse r = service().computeRollup(someManagerId, WEEK, adminPrincipal());

    assertThat(r.members()).isEmpty();
  }

  @Test
  void rollup_emptyTeam_returnsZeroes() {
    UUID managerId = UUID.randomUUID();
    when(plans.findTeamPlans(eq(managerId), eq(WEEK), any(Pageable.class)))
        .thenReturn((Page<WeeklyPlan>) new PageImpl<>(List.<WeeklyPlan>of()));

    RollupResponse r = service().computeRollup(managerId, WEEK, managerPrincipal(managerId));

    assertThat(r.completionPct()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(r.alignmentPct()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(r.tierDistribution())
        .containsEntry("ROCK", 0)
        .containsEntry("PEBBLE", 0)
        .containsEntry("SAND", 0);
    assertThat(r.members()).isEmpty();
  }

  @Test
  void rollup_reflectionPreview_truncatedAt80Chars() {
    UUID managerId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();
    String longNote = "x".repeat(150); // 150 chars; preview should cut to 80
    WeeklyPlan plan = new WeeklyPlan(UUID.randomUUID(), empId, WEEK);
    plan.setState(PlanState.RECONCILED);
    plan.setReconciledAt(FROZEN_NOW.minus(1, ChronoUnit.HOURS));
    plan.setReflectionNote(longNote);
    when(plans.findTeamPlans(eq(managerId), eq(WEEK), any(Pageable.class)))
        .thenReturn((Page<WeeklyPlan>) new PageImpl<>(List.of(plan)));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(plan.getId())).thenReturn(List.of());
    Employee e = new Employee(empId, UUID.randomUUID());
    e.setDisplayName("Cy");
    when(employees.findAllById(any())).thenReturn(List.of(e));

    RollupResponse r = service().computeRollup(managerId, WEEK, managerPrincipal(managerId));

    assertThat(r.members()).hasSize(1);
    assertThat(r.members().get(0).reflectionPreview()).hasSize(80);
  }

  @Test
  void rollup_flagged_membersSortBeforeUnflagged() {
    UUID managerId = UUID.randomUUID();
    UUID empClean = UUID.randomUUID();
    UUID empFlagged = UUID.randomUUID();

    WeeklyPlan cleanPlan = new WeeklyPlan(UUID.randomUUID(), empClean, WEEK);
    cleanPlan.setState(PlanState.RECONCILED);
    cleanPlan.setReconciledAt(FROZEN_NOW.minus(1, ChronoUnit.HOURS));
    cleanPlan.setManagerReviewedAt(FROZEN_NOW); // reviewed -> no UNREVIEWED_72H
    WeeklyCommit cleanRock =
        new WeeklyCommit(
            UUID.randomUUID(), cleanPlan.getId(), "rock", UUID.randomUUID(), ChessTier.ROCK, 0);
    cleanRock.setActualStatus(ActualStatus.DONE);

    WeeklyPlan flaggedPlan = new WeeklyPlan(UUID.randomUUID(), empFlagged, WEEK);
    flaggedPlan.setState(PlanState.RECONCILED);
    flaggedPlan.setReconciledAt(FROZEN_NOW.minus(96, ChronoUnit.HOURS));
    // reconciled but no manager review and >72h -> UNREVIEWED_72H

    when(plans.findTeamPlans(eq(managerId), eq(WEEK), any(Pageable.class)))
        .thenReturn(
            (Page<WeeklyPlan>) new PageImpl<>(List.of(cleanPlan, flaggedPlan))); // clean first
    when(commits.findByPlanIdOrderByDisplayOrderAsc(cleanPlan.getId()))
        .thenReturn(List.of(cleanRock));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(flaggedPlan.getId())).thenReturn(List.of());
    // cleanRock runs through deriveFor — default is "not stuck, streak 1".
    when(derivedFieldService.deriveFor(cleanRock.getId()))
        .thenReturn(new DerivedFieldService.Derived(1, false));
    Employee a = new Employee(empClean, UUID.randomUUID());
    a.setDisplayName("Clean");
    Employee b = new Employee(empFlagged, UUID.randomUUID());
    b.setDisplayName("Flagged");
    when(employees.findAllById(any())).thenReturn(List.of(a, b));

    RollupResponse r = service().computeRollup(managerId, WEEK, managerPrincipal(managerId));

    // Even though cleanPlan was first in the input list, flaggedPlan must come first in output.
    assertThat(r.members()).hasSize(2);
    assertThat(r.members().get(0).employeeId()).isEqualTo(empFlagged);
    assertThat(r.members().get(1).employeeId()).isEqualTo(empClean);
  }

  // --- helpers ---

  private static AuthenticatedPrincipal managerPrincipal(UUID employeeId) {
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", employeeId.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", "UTC",
                "roles", List.of("MANAGER")));
    return new AuthenticatedPrincipal(
        employeeId, UUID.randomUUID(), Optional.empty(), Set.of("MANAGER"), ZoneId.of("UTC"), jwt);
  }

  private static AuthenticatedPrincipal adminPrincipal() {
    UUID adminId = UUID.randomUUID();
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", adminId.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", "UTC",
                "roles", List.of("ADMIN")));
    return new AuthenticatedPrincipal(
        adminId, UUID.randomUUID(), Optional.empty(), Set.of("ADMIN"), ZoneId.of("UTC"), jwt);
  }
}
