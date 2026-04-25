package com.acme.weeklycommit.service;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only audit history for a {@link WeeklyPlan}. Powers {@code GET /api/v1/audit/plans/{id}}.
 *
 * <p>Authz: caller is the plan owner, OR holds {@code MANAGER} role, OR holds {@code ADMIN}. The
 * authorization decision is taken <b>before</b> hitting the audit_log table so a peer IC's audit
 * trail is never read off-disk and into the JVM.
 *
 * <p>Plan must exist; missing plan surfaces as {@link ResourceNotFoundException} (404). The
 * existence check is intentional — without it a caller could probe the audit table for arbitrary
 * UUIDs and see a 200 with empty data, which would leak nothing useful but is needlessly chatty.
 */
@Service
public class AuditService {

  private final WeeklyPlanRepository plans;
  private final AuditLogRepository audits;

  public AuditService(WeeklyPlanRepository plans, AuditLogRepository audits) {
    this.plans = plans;
    this.audits = audits;
  }

  @Transactional(readOnly = true)
  public List<AuditLog> findForPlan(UUID planId, AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", planId));
    boolean isOwner = plan.getEmployeeId().equals(caller.employeeId());
    boolean isManager = caller.isManager();
    boolean isAdmin = caller.hasRole("ADMIN");
    if (!isOwner && !isManager && !isAdmin) {
      throw new AccessDeniedException(
          "caller " + caller.employeeId() + " cannot read audit for plan " + planId);
    }
    return audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
        AuditEntityType.WEEKLY_PLAN, planId);
  }
}
