package com.acme.weeklycommit.service;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only audit history for a {@link WeeklyPlan}. Powers {@code GET /api/v1/audit/plans/{id}}.
 *
 * <p><b>Authz (USER_FLOW.md row 366-367):</b> caller is the plan owner, OR the plan owner's
 * <i>direct</i> manager (looked up via {@link EmployeeRepository}), OR holds {@code ADMIN}. The
 * MANAGER role alone is not enough — a peer manager cannot read another team's audit trail. The
 * decision is taken before hitting {@code audit_log}, so a peer's data is never read off-disk.
 *
 * <p>Plan must exist; missing plan surfaces as {@link ResourceNotFoundException} (404). Without
 * this check the endpoint would return 200 + empty for arbitrary UUIDs, which is needlessly chatty.
 */
@Service
public class AuditService {

  private final WeeklyPlanRepository plans;
  private final AuditLogRepository audits;
  private final EmployeeRepository employees;

  public AuditService(
      WeeklyPlanRepository plans, AuditLogRepository audits, EmployeeRepository employees) {
    this.plans = plans;
    this.audits = audits;
    this.employees = employees;
  }

  @Transactional(readOnly = true)
  public List<AuditLog> findForPlan(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));
    requireReadAccess(plan, caller);
    return audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
        AuditEntityType.WEEKLY_PLAN, planId);
  }

  /**
   * Authz check ordered cheapest-first: owner equality and ADMIN-role checks short-circuit before
   * any DB read. The Employee lookup only fires for MANAGER callers — and only to confirm the
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
        "caller " + caller.employeeId() + " cannot read audit for plan " + plan.getId());
  }

  private boolean isDirectManagerOf(UUID employeeId, UUID candidateManagerId) {
    return employees
        .findById(employeeId)
        .map(Employee::getManagerId)
        .filter(candidateManagerId::equals)
        .isPresent();
  }
}
