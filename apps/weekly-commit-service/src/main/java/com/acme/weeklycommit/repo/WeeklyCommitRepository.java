package com.acme.weeklycommit.repo;

import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.enums.ChessTier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeeklyCommitRepository extends JpaRepository<WeeklyCommit, UUID> {

  List<WeeklyCommit> findByPlanIdOrderByDisplayOrderAsc(UUID planId);

  /**
   * Top Rock: lowest displayOrder Rock for a plan. Uses {@code idx_weekly_commit_toprock} (plan_id,
   * chess_tier, display_order).
   */
  @Query(
      """
          SELECT c FROM WeeklyCommit c
           WHERE c.planId = :planId
             AND c.chessTier = :rock
           ORDER BY c.displayOrder ASC
          """)
  List<WeeklyCommit> findRocksByPlanOrdered(
      @Param("planId") UUID planId, @Param("rock") ChessTier rock);

  default Optional<WeeklyCommit> findTopRock(UUID planId) {
    return findRocksByPlanOrdered(planId, ChessTier.ROCK).stream().findFirst();
  }

  /**
   * Single-hop carry-streak walk. Called iteratively by DerivedFieldService (group 5) up to 52
   * hops. Uses {@code idx_weekly_commit_carry}.
   */
  @Query(
      """
          SELECT c FROM WeeklyCommit c
           WHERE c.id = :commitId
          """)
  Optional<WeeklyCommit> findByIdForStreakWalk(@Param("commitId") UUID commitId);
}
