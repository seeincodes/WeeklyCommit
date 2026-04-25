package com.acme.weeklycommit.repo;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {

  Optional<WeeklyPlan> findByEmployeeIdAndWeekStart(UUID employeeId, LocalDate weekStart);

  /**
   * Auto-lock job: DRAFT plans whose week has reached the configured cutoff. Threshold passed in by
   * caller as an {@link Instant} — never NOW() in SQL (ERROR_FIX_LOG.md / MEMO decision on week
   * math).
   */
  @Query(
      """
          SELECT p FROM WeeklyPlan p
           WHERE p.state = :draftState
             AND p.weekStart <= :cutoffWeekStart
          """)
  List<WeeklyPlan> findDraftsPastCutoff(
      @Param("draftState") PlanState draftState,
      @Param("cutoffWeekStart") LocalDate cutoffWeekStart);

  /** Archival job: RECONCILED plans older than the threshold. */
  @Query(
      """
          SELECT p FROM WeeklyPlan p
           WHERE p.state = :reconciledState
             AND p.reconciledAt < :before
          """)
  List<WeeklyPlan> findReconciledBefore(
      @Param("reconciledState") PlanState reconciledState, @Param("before") Instant before);

  /**
   * Unreviewed-72h digest: RECONCILED plans where the manager hasn't acknowledged and reconcile
   * happened before the threshold. Grouping by manager happens in the service layer.
   */
  @Query(
      """
          SELECT p FROM WeeklyPlan p
           WHERE p.state = :reconciledState
             AND p.managerReviewedAt IS NULL
             AND p.reconciledAt < :threshold
          """)
  List<WeeklyPlan> findUnreviewedReconciledBefore(
      @Param("reconciledState") PlanState reconciledState, @Param("threshold") Instant threshold);

  /**
   * Manager team view: all plans for {@code weekStart} whose employee's {@code manager_id} matches.
   * JOINs {@code Employee} (populated by Auth0 sync) to bridge the relationship, which doesn't
   * exist on {@code WeeklyPlan} directly. Filters out inactive employees so rollups never include
   * deactivated accounts.
   *
   * <p>Hot path for {@code GET /plans/team} and {@code GET /rollup/team}. Paginated; default page
   * size 20, max 100 (enforced in the controller).
   */
  @Query(
      """
          SELECT p FROM WeeklyPlan p, Employee e
           WHERE p.employeeId = e.id
             AND e.managerId = :managerId
             AND e.active = true
             AND p.weekStart = :weekStart
          """)
  Page<WeeklyPlan> findTeamPlans(
      @Param("managerId") UUID managerId,
      @Param("weekStart") LocalDate weekStart,
      Pageable pageable);
}
