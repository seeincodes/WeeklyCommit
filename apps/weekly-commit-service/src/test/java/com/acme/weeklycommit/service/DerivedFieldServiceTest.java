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

  @Test
  void carryStreak_capsAt_52_evenWithLongerChain() {
    // 100-long chain in concept, but the walk should stop at 52. We only stub the 52
    // entries the walk will actually visit (from tip backwards) — stubbing all 100 would
    // trip Mockito STRICT_STUBS because the deeper entries are unreachable.
    //
    // Walk visits ids[99], ids[98], ..., ids[48]  (52 entries inclusive).
    // After reading ids[48] the count hits the cap and the loop exits without calling
    // findByIdForStreakWalk again, so ids[0..47] are never touched.
    int chainLength = 100;
    UUID[] ids = new UUID[chainLength];
    for (int i = 0; i < chainLength; i++) {
      ids[i] = UUID.randomUUID();
    }
    int firstVisited = chainLength - 52; // ids[48] for chainLength=100
    for (int i = firstVisited; i < chainLength; i++) {
      UUID parent = ids[i - 1]; // always non-null in this slice
      when(commits.findByIdForStreakWalk(ids[i])).thenReturn(Optional.of(commit(ids[i], parent)));
    }

    assertThat(service().carryStreak(ids[chainLength - 1])).isEqualTo(52);
  }

  @Test
  void carryStreak_cycleTerminatesAtCap() {
    // A -> B, B -> A. Defensive: infinite walk must be stopped by the cap.
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(commits.findByIdForStreakWalk(a)).thenReturn(Optional.of(commit(a, b)));
    when(commits.findByIdForStreakWalk(b)).thenReturn(Optional.of(commit(b, a)));

    assertThat(service().carryStreak(a)).isEqualTo(52);
  }

  // --- stuckFlag ---

  @Test
  void stuckFlag_streakOf2_isFalse() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(commits.findByIdForStreakWalk(b)).thenReturn(Optional.of(commit(b, a)));
    when(commits.findByIdForStreakWalk(a)).thenReturn(Optional.of(commit(a, null)));

    assertThat(service().stuckFlag(b)).isFalse();
  }

  @Test
  void stuckFlag_streakOf3_isTrue() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    when(commits.findByIdForStreakWalk(c)).thenReturn(Optional.of(commit(c, b)));
    when(commits.findByIdForStreakWalk(b)).thenReturn(Optional.of(commit(b, a)));
    when(commits.findByIdForStreakWalk(a)).thenReturn(Optional.of(commit(a, null)));

    assertThat(service().stuckFlag(c)).isTrue();
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
