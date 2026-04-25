package com.acme.weeklycommit.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.testsupport.JpaTestSlice;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@JpaTestSlice
class WeeklyPlanRepositoryIT {

  @Autowired private WeeklyPlanRepository plans;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    PostgresTestContainer.register(r);
    // Placeholder AUTH0_* so application.yml doesn't fail placeholder binding if those
    // properties get eagerly resolved by an unexpected auto-config pull.
    r.add("AUTH0_ISSUER_URI", () -> "https://test.invalid/");
    r.add("AUTH0_AUDIENCE", () -> "test-audience");
  }

  @Test
  void findByEmployeeIdAndWeekStart_found() {
    UUID employeeId = UUID.randomUUID();
    LocalDate week = LocalDate.parse("2026-04-27");
    plans.save(new WeeklyPlan(UUID.randomUUID(), employeeId, week));

    assertThat(plans.findByEmployeeIdAndWeekStart(employeeId, week)).isPresent();
  }

  @Test
  void findByEmployeeIdAndWeekStart_notFound() {
    assertThat(plans.findByEmployeeIdAndWeekStart(UUID.randomUUID(), LocalDate.parse("2026-04-27")))
        .isEmpty();
  }

  @Test
  void findDraftsPastCutoff_onlyReturnsDraftsAtOrBeforeCutoff() {
    UUID emp = UUID.randomUUID();
    WeeklyPlan oldDraft =
        save(new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-04-20")));
    save(
        withState(
            new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-04-27")),
            PlanState.LOCKED));
    save(
        new WeeklyPlan(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-05-04"))); // future draft

    List<WeeklyPlan> results =
        plans.findDraftsPastCutoff(PlanState.DRAFT, LocalDate.parse("2026-04-27"));

    assertThat(results).extracting(WeeklyPlan::getId).containsExactly(oldDraft.getId());
  }

  @Test
  void findDraftsPastCutoff_includesCutoffDateExactly() {
    UUID emp = UUID.randomUUID();
    WeeklyPlan exact = save(new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-04-27")));

    List<WeeklyPlan> results =
        plans.findDraftsPastCutoff(PlanState.DRAFT, LocalDate.parse("2026-04-27"));

    assertThat(results).extracting(WeeklyPlan::getId).contains(exact.getId());
  }

  @Test
  void findReconciledBefore_onlyReturnsReconciledOlderThanThreshold() {
    UUID emp = UUID.randomUUID();
    Instant now = Instant.now();

    WeeklyPlan aged =
        save(
            withReconciled(
                new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-01-05")),
                now.minus(100, ChronoUnit.DAYS)));
    save(
        withReconciled(
            new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-02-02")),
            now.minus(30, ChronoUnit.DAYS))); // not aged
    save(
        withState(
            new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-01-12")),
            PlanState.LOCKED)); // wrong state

    List<WeeklyPlan> results =
        plans.findReconciledBefore(PlanState.RECONCILED, now.minus(90, ChronoUnit.DAYS));

    assertThat(results).extracting(WeeklyPlan::getId).containsExactly(aged.getId());
  }

  @Test
  void findUnreviewedReconciledBefore_requiresManagerReviewedAtNullAndReconciledBeforeThreshold() {
    UUID emp = UUID.randomUUID();
    Instant now = Instant.now();
    Instant threshold = now.minus(72, ChronoUnit.HOURS);

    WeeklyPlan unreviewedAged =
        save(
            withReconciled(
                new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-03-02")),
                now.minus(96, ChronoUnit.HOURS)));

    WeeklyPlan reviewedAged =
        save(
            withManagerReviewed(
                withReconciled(
                    new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-03-09")),
                    now.minus(120, ChronoUnit.HOURS)),
                now.minus(24, ChronoUnit.HOURS)));

    WeeklyPlan unreviewedFresh =
        save(
            withReconciled(
                new WeeklyPlan(UUID.randomUUID(), emp, LocalDate.parse("2026-03-16")),
                now.minus(12, ChronoUnit.HOURS)));

    List<WeeklyPlan> results =
        plans.findUnreviewedReconciledBefore(PlanState.RECONCILED, threshold);

    assertThat(results).extracting(WeeklyPlan::getId).containsExactly(unreviewedAged.getId());
    assertThat(results)
        .extracting(WeeklyPlan::getId)
        .doesNotContain(reviewedAged.getId(), unreviewedFresh.getId());
  }

  // --- helpers ---

  private WeeklyPlan save(WeeklyPlan p) {
    return plans.saveAndFlush(p);
  }

  private static WeeklyPlan withState(WeeklyPlan p, PlanState s) {
    p.setState(s);
    return p;
  }

  private static WeeklyPlan withReconciled(WeeklyPlan p, Instant at) {
    p.setState(PlanState.RECONCILED);
    p.setReconciledAt(at);
    return p;
  }

  private static WeeklyPlan withManagerReviewed(WeeklyPlan p, Instant at) {
    p.setManagerReviewedAt(at);
    return p;
  }
}
