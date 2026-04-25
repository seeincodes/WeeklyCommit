package com.acme.weeklycommit.service;

import com.acme.weeklycommit.api.dto.CreateReviewRequest;
import com.acme.weeklycommit.api.exception.InvalidStateTransitionException;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.ManagerReview;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.domain.enums.AuditEventType;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.ManagerReviewRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager review operations on a {@code WeeklyPlan}. Owns three concerns:
 *
 * <ul>
 *   <li>Authz: only MANAGER can create reviews; self-or-MANAGER can list.
 *   <li>State guard: reviews are only valid on RECONCILED plans.
 *   <li>Side effects: creating a review sets {@code plan.managerReviewedAt} and appends an {@code
 *       audit_log} row in the same transaction.
 * </ul>
 */
@Service
public class ManagerReviewService {

  private final WeeklyPlanRepository plans;
  private final ManagerReviewRepository reviews;
  private final AuditLogRepository audits;
  private final EmployeeRepository employees;
  private final Clock clock;

  public ManagerReviewService(
      WeeklyPlanRepository plans,
      ManagerReviewRepository reviews,
      AuditLogRepository audits,
      EmployeeRepository employees,
      Clock clock) {
    this.plans = plans;
    this.reviews = reviews;
    this.audits = audits;
    this.employees = employees;
    this.clock = clock;
  }

  /**
   * Create a manager review on a RECONCILED plan. Caller must hold the {@code MANAGER} role — the
   * plan owner cannot review themselves. Side effects: stamps {@code plan.managerReviewedAt},
   * appends a {@code MANAGER_REVIEW} audit row.
   */
  @Transactional
  public ManagerReview createReview(
      UUID planId, CreateReviewRequest request, AuthenticatedPrincipal caller) {
    if (!caller.isManager()) {
      throw new AccessDeniedException(
          "caller " + caller.employeeId() + " lacks MANAGER role; cannot create reviews");
    }
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));
    if (plan.getState() != PlanState.RECONCILED) {
      throw new InvalidStateTransitionException(
          plan.getState().name(), "RECONCILED", "reviews are only valid on RECONCILED plans");
    }

    Instant now = Instant.now(clock);
    ManagerReview review = new ManagerReview(UUID.randomUUID(), planId, caller.employeeId(), now);
    review.setComment(request.comment());
    ManagerReview saved = reviews.save(review);

    plan.setManagerReviewedAt(now);
    plans.save(plan);

    AuditLog audit =
        new AuditLog(
            UUID.randomUUID(),
            AuditEntityType.MANAGER_REVIEW,
            saved.getId(),
            AuditEventType.MANAGER_REVIEW,
            caller.employeeId());
    audits.save(audit);

    return saved;
  }

  /**
   * List reviews on a plan. Authz follows USER_FLOW.md row 366-367: plan owner OR plan owner's
   * <i>direct</i> manager OR ADMIN. The plan load must happen first so {@code plan.employeeId} is
   * available for the owner-equality and direct-manager checks.
   */
  @Transactional(readOnly = true)
  public List<ManagerReview> listReviews(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));
    requireReadAccess(plan, caller);
    return reviews.findByPlanIdOrderByAcknowledgedAtAsc(planId);
  }

  /**
   * Authz check ordered cheapest-first: owner equality and ADMIN-role short-circuit before any DB
   * read. The Employee lookup only fires for MANAGER callers, and only to confirm the
   * direct-manager relationship.
   */
  private void requireReadAccess(WeeklyPlan plan, AuthenticatedPrincipal caller) {
    if (plan.getEmployeeId().equals(caller.employeeId())) {
      return; // self
    }
    if (caller.hasRole("ADMIN")) {
      return; // skip-level / ops scope
    }
    if (caller.isManager() && isDirectManagerOf(plan.getEmployeeId(), caller.employeeId())) {
      return;
    }
    throw new AccessDeniedException(
        "caller " + caller.employeeId() + " cannot read reviews on plan " + plan.getId());
  }

  private boolean isDirectManagerOf(UUID employeeId, UUID candidateManagerId) {
    return employees
        .findById(employeeId)
        .map(Employee::getManagerId)
        .filter(candidateManagerId::equals)
        .isPresent();
  }
}
