package com.acme.weeklycommit.service;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write operations against {@link WeeklyCommit}. Authz decided here (service boundary) per
 * the project memory. Derived-field enrichment is a controller concern — this service returns
 * raw entities.
 *
 * <p>Every method that takes a {@code planId} first loads the plan for authz, then reads/writes
 * the commit. The plan load is unavoidable: commits carry {@code planId} but not {@code
 * employeeId}, and authz is scoped to "caller owns the plan" (or MANAGER role).
 */
@Service
public class WeeklyCommitService {

  private final WeeklyPlanRepository plans;
  private final WeeklyCommitRepository commits;

  public WeeklyCommitService(WeeklyPlanRepository plans, WeeklyCommitRepository commits) {
    this.plans = plans;
    this.commits = commits;
  }

  /**
   * List commits for a plan, ordered by {@code displayOrder}. Self-or-MANAGER: caller must own
   * the plan or hold the MANAGER role. 404 if the plan is missing (raised before the commits
   * query so a peer's plan presence can't be probed via timing).
   */
  @Transactional(readOnly = true)
  public List<WeeklyCommit> findCommitsForPlan(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan = requireReadableByCaller(planId, caller);
    return commits.findByPlanIdOrderByDisplayOrderAsc(plan.getId());
  }

  /**
   * Load the plan and enforce self-or-MANAGER authz. Throws {@link ResourceNotFoundException} if
   * missing (so peer existence can't be probed) and {@link AccessDeniedException} if the caller
   * is unrelated.
   */
  private WeeklyPlan requireReadableByCaller(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));
    if (!plan.getEmployeeId().equals(caller.employeeId()) && !caller.isManager()) {
      throw new AccessDeniedException(
          "caller "
              + caller.employeeId()
              + " cannot access commits on plan "
              + planId);
    }
    return plan;
  }
}
