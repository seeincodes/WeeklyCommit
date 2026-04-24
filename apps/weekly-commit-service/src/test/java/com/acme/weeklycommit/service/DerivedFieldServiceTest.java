package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DerivedFieldServiceTest {

  @Mock private WeeklyCommitRepository commits;

  private DerivedFieldService service() {
    return new DerivedFieldService(commits);
  }

  // --- topRock ---

  @Test
  void topRock_noRocks_returnsEmpty() {
    UUID planId = UUID.randomUUID();
    when(commits.findTopRock(planId)).thenReturn(Optional.empty());

    assertThat(service().topRock(planId)).isEmpty();
  }

  @Test
  void topRock_returnsRepoResult() {
    UUID planId = UUID.randomUUID();
    WeeklyCommit rock =
        new WeeklyCommit(
            UUID.randomUUID(), planId, "land spike", UUID.randomUUID(), ChessTier.ROCK, 0);
    when(commits.findTopRock(planId)).thenReturn(Optional.of(rock));

    assertThat(service().topRock(planId)).contains(rock);
  }

  // --- carryStreak ---

  @Test
  void carryStreak_orphanCommit_is_1() {
    // Fresh commit, never carried from anywhere: streak = 1 (just itself).
    UUID id = UUID.randomUUID();
    WeeklyCommit orphan = commit(id, null);
    when(commits.findByIdForStreakWalk(id)).thenReturn(Optional.of(orphan));

    assertThat(service().carryStreak(id)).isEqualTo(1);
  }

  @Test
  void carryStreak_chainOfThree_is_3() {
    // origin A (null parent) <- carried B (parent=A) <- carried C (parent=B)
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    when(commits.findByIdForStreakWalk(c)).thenReturn(Optional.of(commit(c, b)));
    when(commits.findByIdForStreakWalk(b)).thenReturn(Optional.of(commit(b, a)));
    when(commits.findByIdForStreakWalk(a)).thenReturn(Optional.of(commit(a, null)));

    assertThat(service().carryStreak(c)).isEqualTo(3);
  }

  // --- helpers ---

  private static WeeklyCommit commit(UUID id, UUID carriedForwardFromId) {
    WeeklyCommit c =
        new WeeklyCommit(
            id, UUID.randomUUID(), "x", UUID.randomUUID(), ChessTier.PEBBLE, 0);
    c.setCarriedForwardFromId(carriedForwardFromId);
    return c;
  }
}
