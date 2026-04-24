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

  private final WeeklyCommitRepository commits;

  public DerivedFieldService(WeeklyCommitRepository commits) {
    this.commits = commits;
  }

  /** Lowest-displayOrder ROCK for the given plan, or empty if the IC has no Rocks. */
  public Optional<WeeklyCommit> topRock(UUID planId) {
    return commits.findTopRock(planId);
  }
}
