package com.acme.weeklycommit.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.testsupport.JpaTestSlice;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@JpaTestSlice
class WeeklyCommitRepositoryIT {

  @Autowired private WeeklyCommitRepository commits;
  @Autowired private WeeklyPlanRepository plans;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    PostgresTestContainer.register(r);
    r.add("AUTH0_ISSUER_URI", () -> "https://test.invalid/");
    r.add("AUTH0_AUDIENCE", () -> "test-audience");
  }

  @Test
  void findByPlanIdOrderByDisplayOrderAsc_sortsByDisplayOrder() {
    WeeklyPlan plan = savedPlan();
    commits.saveAndFlush(commit(plan.getId(), ChessTier.SAND, 2));
    commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 0));
    commits.saveAndFlush(commit(plan.getId(), ChessTier.PEBBLE, 1));

    List<WeeklyCommit> ordered = commits.findByPlanIdOrderByDisplayOrderAsc(plan.getId());

    assertThat(ordered).extracting(WeeklyCommit::getDisplayOrder).containsExactly(0, 1, 2);
  }

  @Test
  void findRocksByPlanOrdered_returnsOnlyRocks_sortedByDisplayOrder() {
    WeeklyPlan plan = savedPlan();
    commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 2));
    commits.saveAndFlush(commit(plan.getId(), ChessTier.PEBBLE, 0)); // excluded
    WeeklyCommit rock0 = commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 0));
    commits.saveAndFlush(commit(plan.getId(), ChessTier.SAND, 1));   // excluded
    commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 5));

    List<WeeklyCommit> rocks = commits.findRocksByPlanOrdered(plan.getId(), ChessTier.ROCK);

    assertThat(rocks).allMatch(c -> c.getChessTier() == ChessTier.ROCK);
    assertThat(rocks).extracting(WeeklyCommit::getDisplayOrder).containsExactly(0, 2, 5);
    assertThat(rocks.get(0).getId()).isEqualTo(rock0.getId());
  }

  @Test
  void findTopRock_returnsLowestDisplayOrderRock() {
    WeeklyPlan plan = savedPlan();
    commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 3));
    WeeklyCommit top = commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 1));
    commits.saveAndFlush(commit(plan.getId(), ChessTier.PEBBLE, 0)); // lower but not ROCK
    commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 7));

    Optional<WeeklyCommit> topRock = commits.findTopRock(plan.getId());

    assertThat(topRock).isPresent();
    assertThat(topRock.get().getId()).isEqualTo(top.getId());
    assertThat(topRock.get().getChessTier()).isEqualTo(ChessTier.ROCK);
  }

  @Test
  void findTopRock_noRocks_returnsEmpty_andCountsAsManagerFlag() {
    WeeklyPlan plan = savedPlan();
    commits.saveAndFlush(commit(plan.getId(), ChessTier.PEBBLE, 0));
    commits.saveAndFlush(commit(plan.getId(), ChessTier.SAND, 1));

    assertThat(commits.findTopRock(plan.getId())).isEmpty();
  }

  @Test
  void findByIdForStreakWalk_returnsThatCommit() {
    WeeklyPlan plan = savedPlan();
    WeeklyCommit saved = commits.saveAndFlush(commit(plan.getId(), ChessTier.ROCK, 0));

    Optional<WeeklyCommit> walked = commits.findByIdForStreakWalk(saved.getId());

    assertThat(walked).isPresent();
    assertThat(walked.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  void findByIdForStreakWalk_missing_returnsEmpty() {
    assertThat(commits.findByIdForStreakWalk(UUID.randomUUID())).isEmpty();
  }

  // --- helpers ---

  private WeeklyPlan savedPlan() {
    return plans.saveAndFlush(
        new WeeklyPlan(UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-04-27")));
  }

  private static WeeklyCommit commit(UUID planId, ChessTier tier, int displayOrder) {
    return new WeeklyCommit(
        UUID.randomUUID(), planId, "commit #" + displayOrder, UUID.randomUUID(), tier, displayOrder);
  }
}
