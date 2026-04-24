package com.acme.weeklycommit.service;

import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Pure-function derivations over commit data. No mutation. See docs/MEMO.md decision #7 (Top Rock
 * derived, not stored) and presearch §3 Invariants (carry-streak walk capped at 52 hops).
 *
 * <p>Applied uniformly in response mappers so every read goes through the same logic.
 */
@Service
public class DerivedFieldService {

  /** Carry-streak walk cap — defensive against pathological chains and cycles. */
  static final int CARRY_STREAK_CAP = 52;

  private final WeeklyCommitRepository commits;

  public DerivedFieldService(WeeklyCommitRepository commits) {
    this.commits = commits;
  }

  /** Lowest-displayOrder ROCK for the given plan, or empty if the IC has no Rocks. */
  public Optional<WeeklyCommit> topRock(UUID planId) {
    return commits.findTopRock(planId);
  }

  /** Streak threshold at which a commit is flagged as stuck (presearch §3). */
  static final int STUCK_FLAG_THRESHOLD = 3;

  /** {@code true} when the commit has been carried forward 3+ consecutive weeks. */
  public boolean stuckFlag(UUID commitId) {
    return carryStreak(commitId) >= STUCK_FLAG_THRESHOLD;
  }

  /**
   * Chain length for a carry-forward streak. Walks backwards through {@code carriedForwardFromId}
   * until the chain ends, reaches the cap, or hits a missing predecessor. Inclusive of the given
   * commit: an orphan (never carried) returns 1.
   */
  public int carryStreak(UUID commitId) {
    Optional<WeeklyCommit> head = commits.findByIdForStreakWalk(commitId);
    if (head.isEmpty()) {
      return 0;
    }
    int count = 1;
    UUID next = head.get().getCarriedForwardFromId();
    while (next != null && count < CARRY_STREAK_CAP) {
      Optional<WeeklyCommit> predecessor = commits.findByIdForStreakWalk(next);
      if (predecessor.isEmpty()) {
        break;
      }
      count++;
      next = predecessor.get().getCarriedForwardFromId();
    }
    return count;
  }
}
