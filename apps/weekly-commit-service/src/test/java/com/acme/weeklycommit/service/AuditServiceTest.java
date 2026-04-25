package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.domain.enums.AuditEventType;
import com.acme.weeklycommit.repo.AuditLogRepository;
import com.acme.weeklycommit.repo.EmployeeRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

  @Mock private WeeklyPlanRepository plans;
  @Mock private AuditLogRepository audits;
  @Mock private EmployeeRepository employees;

  private AuditService service() {
    return new AuditService(plans, audits, employees);
  }

  @Test
  void findForPlan_selfOwner_returnsAuditList() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    AuditLog row =
        new AuditLog(
            UUID.randomUUID(),
            AuditEntityType.WEEKLY_PLAN,
            planId,
            AuditEventType.STATE_TRANSITION,
            employeeId);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            AuditEntityType.WEEKLY_PLAN, planId))
        .thenReturn(List.of(row));

    List<AuditLog> result = service().findForPlan(planId, icPrincipal(employeeId));

    assertThat(result).containsExactly(row);
    // Self-owner short-circuit: no employee lookup needed.
    verify(employees, never()).findById(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void findForPlan_directManager_returnsAuditList() {
    UUID planId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, owner, LocalDate.parse("2026-04-27"));
    Employee ownerRow = new Employee(owner, UUID.randomUUID());
    ownerRow.setManagerId(managerId);
    AuditLog row =
        new AuditLog(
            UUID.randomUUID(),
            AuditEntityType.WEEKLY_PLAN,
            planId,
            AuditEventType.MANAGER_REVIEW,
            UUID.randomUUID());
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(employees.findById(owner)).thenReturn(Optional.of(ownerRow));
    when(audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            AuditEntityType.WEEKLY_PLAN, planId))
        .thenReturn(List.of(row));

    List<AuditLog> result = service().findForPlan(planId, managerPrincipal(managerId));

    assertThat(result).containsExactly(row);
  }

  @Test
  void findForPlan_managerOfDifferentEmployee_throwsAccessDenied() {
    // Tightened authz: a MANAGER role alone is not enough -- the caller must be the plan
    // owner's *direct* manager. A manager querying a peer manager's team is rejected.
    UUID planId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    UUID actualManager = UUID.randomUUID();
    UUID peerManager = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, owner, LocalDate.parse("2026-04-27"));
    Employee ownerRow = new Employee(owner, UUID.randomUUID());
    ownerRow.setManagerId(actualManager);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(employees.findById(owner)).thenReturn(Optional.of(ownerRow));

    assertThatThrownBy(() -> service().findForPlan(planId, managerPrincipal(peerManager)))
        .isInstanceOf(AccessDeniedException.class);

    verify(audits, never())
        .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void findForPlan_managerOfPlanWithUnassignedOwner_throwsAccessDenied() {
    // Edge case: plan owner has no manager (manager_id IS NULL). Only ADMIN can read it;
    // a random MANAGER cannot claim authority over an unassigned employee's plan.
    UUID planId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, owner, LocalDate.parse("2026-04-27"));
    Employee ownerRow = new Employee(owner, UUID.randomUUID());
    // managerId not set -> null
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(employees.findById(owner)).thenReturn(Optional.of(ownerRow));

    assertThatThrownBy(() -> service().findForPlan(planId, managerPrincipal(UUID.randomUUID())))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void findForPlan_admin_returnsAuditList() {
    // ADMIN bypasses the direct-manager check entirely (skip-level / ops scope).
    UUID planId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    AuditLog row =
        new AuditLog(
            UUID.randomUUID(),
            AuditEntityType.WEEKLY_PLAN,
            planId,
            AuditEventType.STATE_TRANSITION,
            UUID.randomUUID());
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            AuditEntityType.WEEKLY_PLAN, planId))
        .thenReturn(List.of(row));

    List<AuditLog> result = service().findForPlan(planId, adminPrincipal());

    assertThat(result).containsExactly(row);
    // ADMIN short-circuit: no employee lookup needed.
    verify(employees, never()).findById(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void findForPlan_nonOwnerIc_throwsAccessDenied() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));

    assertThatThrownBy(() -> service().findForPlan(planId, icPrincipal(UUID.randomUUID())))
        .isInstanceOf(AccessDeniedException.class);

    verify(audits, never())
        .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void findForPlan_planNotFound_throwsResourceNotFound() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().findForPlan(planId, icPrincipal(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(audits, never())
        .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void findForPlan_emptyAudit_returnsEmptyList() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(audits.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            AuditEntityType.WEEKLY_PLAN, planId))
        .thenReturn(List.of());

    assertThat(service().findForPlan(planId, icPrincipal(employeeId))).isEmpty();
  }

  // --- helpers ---

  private static AuthenticatedPrincipal icPrincipal(UUID employeeId) {
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", employeeId.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", "UTC"));
    return new AuthenticatedPrincipal(
        employeeId, UUID.randomUUID(), Optional.empty(), Set.of(), ZoneId.of("UTC"), jwt);
  }

  private static AuthenticatedPrincipal managerPrincipal(UUID employeeId) {
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", employeeId.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", "UTC",
                "roles", List.of("MANAGER")));
    return new AuthenticatedPrincipal(
        employeeId, UUID.randomUUID(), Optional.empty(), Set.of("MANAGER"), ZoneId.of("UTC"), jwt);
  }

  private static AuthenticatedPrincipal adminPrincipal() {
    UUID adminId = UUID.randomUUID();
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", adminId.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", "UTC",
                "roles", List.of("ADMIN")));
    return new AuthenticatedPrincipal(
        adminId, UUID.randomUUID(), Optional.empty(), Set.of("ADMIN"), ZoneId.of("UTC"), jwt);
  }
}
