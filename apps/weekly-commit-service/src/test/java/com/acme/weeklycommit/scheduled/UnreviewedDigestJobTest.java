package com.acme.weeklycommit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UnreviewedDigestJob}. Mirrors the {@code AutoLockJobTest} shape: a small
 * fixed {@link Clock}, mocked repositories, and assertions on the {@code DigestRunSummary} record
 * returned by {@code runOnce()}.
 *
 * <p>Per ADR-0002 the actual {@code WEEKLY_UNREVIEWED_DIGEST} dispatch is deferred -- v1 verifies
 * the skip-level grouping logic only.
 */
@ExtendWith(MockitoExtension.class)
class UnreviewedDigestJobTest {

  @Mock private WeeklyPlanRepository plans;
  @Mock private EmployeeRepository employees;

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-27T09:00:00Z"), ZoneId.of("UTC"));

  private UnreviewedDigestJob job(Clock clock, int thresholdHours) {
    return new UnreviewedDigestJob(plans, employees, clock, thresholdHours);
  }

  private static WeeklyPlan plan(UUID id, UUID employeeId) {
    return new WeeklyPlan(id, employeeId, LocalDate.parse("2026-04-20"));
  }

  private static Employee emp(UUID id, UUID managerId) {
    Employee e = new Employee(id, UUID.randomUUID());
    e.setManagerId(managerId);
    return e;
  }

  @Test
  void runOnce_groupsBySkipLevelManager() {
    // 3 plans:
    //   A: owner emp1, manager emp2, skip-level emp3
    //   B: owner emp4, manager emp2 (same!), skip-level emp3
    //   C: owner emp5, manager emp6, skip-level emp7
    // Expect 2 skip-level groups: emp3 -> [A, B], emp7 -> [C]
    UUID emp1 = UUID.randomUUID();
    UUID emp2 = UUID.randomUUID();
    UUID emp3 = UUID.randomUUID();
    UUID emp4 = UUID.randomUUID();
    UUID emp5 = UUID.randomUUID();
    UUID emp6 = UUID.randomUUID();
    UUID emp7 = UUID.randomUUID();

    UUID planA = UUID.randomUUID();
    UUID planB = UUID.randomUUID();
    UUID planC = UUID.randomUUID();

    when(plans.findUnreviewedReconciledBefore(
            eq(PlanState.RECONCILED), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(plan(planA, emp1), plan(planB, emp4), plan(planC, emp5)));

    when(employees.findById(emp1)).thenReturn(Optional.of(emp(emp1, emp2)));
    when(employees.findById(emp4)).thenReturn(Optional.of(emp(emp4, emp2)));
    when(employees.findById(emp5)).thenReturn(Optional.of(emp(emp5, emp6)));
    when(employees.findById(emp2)).thenReturn(Optional.of(emp(emp2, emp3)));
    when(employees.findById(emp6)).thenReturn(Optional.of(emp(emp6, emp7)));

    UnreviewedDigestJob.DigestRunSummary result = job(FIXED_CLOCK, 72).runOnce();

    assertThat(result.plansFound()).isEqualTo(3);
    assertThat(result.skipLevelGroups()).isEqualTo(2);
    assertThat(result.unmanagedCount()).isZero();
    assertThat(result.noSkipLevelCount()).isZero();
  }

  @Test
  void runOnce_thresholdMath_72h() {
    when(plans.findUnreviewedReconciledBefore(
            eq(PlanState.RECONCILED), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

    job(FIXED_CLOCK, 72).runOnce();

    ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
    verify(plans).findUnreviewedReconciledBefore(eq(PlanState.RECONCILED), threshold.capture());
    Instant expected = FIXED_CLOCK.instant().minus(72, ChronoUnit.HOURS);
    assertThat(threshold.getValue()).isEqualTo(expected);
  }

  @Test
  void runOnce_unmanagedEmployee_putInUnmanagedBucket() {
    // Owner has no manager -> goes to unmanaged bucket, not a skip-level group.
    UUID owner = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findUnreviewedReconciledBefore(
            eq(PlanState.RECONCILED), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(plan(planId, owner)));
    when(employees.findById(owner)).thenReturn(Optional.of(emp(owner, null)));

    UnreviewedDigestJob.DigestRunSummary result = job(FIXED_CLOCK, 72).runOnce();

    assertThat(result.plansFound()).isEqualTo(1);
    assertThat(result.skipLevelGroups()).isZero();
    assertThat(result.unmanagedCount()).isEqualTo(1);
    assertThat(result.noSkipLevelCount()).isZero();
  }

  @Test
  void runOnce_directManagerHasNoSkipLevel_putInNoSkipLevelBucket() {
    // Owner has a manager, but the manager is org-leaf (no manager of their own).
    UUID owner = UUID.randomUUID();
    UUID directManager = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    when(plans.findUnreviewedReconciledBefore(
            eq(PlanState.RECONCILED), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(plan(planId, owner)));
    when(employees.findById(owner)).thenReturn(Optional.of(emp(owner, directManager)));
    when(employees.findById(directManager)).thenReturn(Optional.of(emp(directManager, null)));

    UnreviewedDigestJob.DigestRunSummary result = job(FIXED_CLOCK, 72).runOnce();

    assertThat(result.plansFound()).isEqualTo(1);
    assertThat(result.skipLevelGroups()).isZero();
    assertThat(result.unmanagedCount()).isZero();
    assertThat(result.noSkipLevelCount()).isEqualTo(1);
  }

  @Test
  void runOnce_emptyResult() {
    when(plans.findUnreviewedReconciledBefore(
            eq(PlanState.RECONCILED), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

    UnreviewedDigestJob.DigestRunSummary result = job(FIXED_CLOCK, 72).runOnce();

    assertThat(result.plansFound()).isZero();
    assertThat(result.skipLevelGroups()).isZero();
    assertThat(result.unmanagedCount()).isZero();
    assertThat(result.noSkipLevelCount()).isZero();
    verifyNoInteractions(employees);
  }

  @Test
  void runOnce_employeeRepoLookupFailure_doesNotAbortBatch() {
    // Owner-1 lookup blows up; owner-2 must still be grouped.
    UUID owner1 = UUID.randomUUID();
    UUID owner2 = UUID.randomUUID();
    UUID manager2 = UUID.randomUUID();
    UUID skipLevel2 = UUID.randomUUID();
    UUID plan1 = UUID.randomUUID();
    UUID plan2 = UUID.randomUUID();

    when(plans.findUnreviewedReconciledBefore(
            eq(PlanState.RECONCILED), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(plan(plan1, owner1), plan(plan2, owner2)));
    when(employees.findById(owner1)).thenThrow(new RuntimeException("simulated DB blip on owner1"));
    when(employees.findById(owner2)).thenReturn(Optional.of(emp(owner2, manager2)));
    when(employees.findById(manager2)).thenReturn(Optional.of(emp(manager2, skipLevel2)));

    UnreviewedDigestJob.DigestRunSummary result = job(FIXED_CLOCK, 72).runOnce();

    assertThat(result.plansFound()).isEqualTo(2);
    assertThat(result.skipLevelGroups()).isEqualTo(1);
    // The failed plan does not increment unmanaged or noSkipLevel buckets;
    // it's logged at WARN as a lookup failure and skipped.
    verify(employees).findById(owner1);
    verify(employees).findById(owner2);
    verify(employees, never()).findById(eq(UUID.randomUUID())); // sanity, never matches
  }
}
