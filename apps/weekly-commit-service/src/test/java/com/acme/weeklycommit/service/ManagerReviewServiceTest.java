package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ManagerReviewServiceTest {

  private static final Instant FROZEN_NOW = Instant.parse("2026-05-05T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FROZEN_NOW, ZoneId.of("UTC"));

  @Mock private WeeklyPlanRepository plans;
  @Mock private ManagerReviewRepository reviews;
  @Mock private AuditLogRepository audits;
  @Mock private EmployeeRepository employees;

  private ManagerReviewService service() {
    return new ManagerReviewService(plans, reviews, audits, employees, FIXED_CLOCK);
  }

  @Test
  void createReview_managerOnReconciledPlan_savesReview_setsManagerReviewedAt_appendsAudit() {
    UUID planId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    plan.setState(PlanState.RECONCILED);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    when(reviews.save(any(ManagerReview.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateReviewRequest req = new CreateReviewRequest("good week, ship it");

    ManagerReview saved = service().createReview(planId, req, managerPrincipal(managerId));

    assertThat(saved.getPlanId()).isEqualTo(planId);
    assertThat(saved.getManagerId()).isEqualTo(managerId);
    assertThat(saved.getComment()).isEqualTo("good week, ship it");
    assertThat(saved.getAcknowledgedAt()).isEqualTo(FROZEN_NOW);
    assertThat(plan.getManagerReviewedAt()).isEqualTo(FROZEN_NOW);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(audits).save(auditCaptor.capture());
    AuditLog audit = auditCaptor.getValue();
    assertThat(audit.getEntityType()).isEqualTo(AuditEntityType.MANAGER_REVIEW);
    assertThat(audit.getEntityId()).isEqualTo(saved.getId());
    assertThat(audit.getEventType()).isEqualTo(AuditEventType.MANAGER_REVIEW);
    assertThat(audit.getActorId()).isEqualTo(managerId);
  }

  @Test
  void createReview_nullComment_isAcceptable() {
    UUID planId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    plan.setState(PlanState.RECONCILED);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    when(reviews.save(any(ManagerReview.class))).thenAnswer(inv -> inv.getArgument(0));

    ManagerReview saved =
        service().createReview(planId, new CreateReviewRequest(null), managerPrincipal(managerId));

    assertThat(saved.getComment()).isNull();
  }

  @Test
  void createReview_icCaller_throwsAccessDenied_evenIfPlanOwner() {
    // Plan owner is NOT entitled to "review" themselves -- reviews are a manager-only act.
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    plan.setState(PlanState.RECONCILED);
    // No stubbing of plans.findById -- caller is rejected before the load.

    assertThatThrownBy(
            () ->
                service()
                    .createReview(planId, new CreateReviewRequest(null), icPrincipal(employeeId)))
        .isInstanceOf(AccessDeniedException.class);

    verify(plans, never()).findById(any());
    verify(reviews, never()).save(any(ManagerReview.class));
  }

  @Test
  void createReview_planNotReconciled_rejected() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    plan.setState(PlanState.LOCKED);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));

    assertThatThrownBy(
            () ->
                service()
                    .createReview(
                        planId, new CreateReviewRequest(null), managerPrincipal(UUID.randomUUID())))
        .isInstanceOf(InvalidStateTransitionException.class);

    verify(reviews, never()).save(any(ManagerReview.class));
    verify(audits, never()).save(any(AuditLog.class));
  }

  @Test
  void createReview_planNotFound_throwsResourceNotFound() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service()
                    .createReview(
                        planId, new CreateReviewRequest(null), managerPrincipal(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // --- listReviews ---

  @Test
  void listReviews_selfOwner_returnsList() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    ManagerReview r =
        new ManagerReview(UUID.randomUUID(), planId, UUID.randomUUID(), Instant.now());
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(reviews.findByPlanIdOrderByAcknowledgedAtAsc(planId)).thenReturn(List.of(r));

    assertThat(service().listReviews(planId, icPrincipal(employeeId))).containsExactly(r);
  }

  @Test
  void listReviews_directManager_returnsList() {
    UUID planId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, owner, LocalDate.parse("2026-04-27"));
    Employee ownerRow = new Employee(owner, UUID.randomUUID());
    ownerRow.setManagerId(managerId);
    ManagerReview r =
        new ManagerReview(UUID.randomUUID(), planId, UUID.randomUUID(), Instant.now());
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(employees.findById(owner)).thenReturn(Optional.of(ownerRow));
    when(reviews.findByPlanIdOrderByAcknowledgedAtAsc(planId)).thenReturn(List.of(r));

    assertThat(service().listReviews(planId, managerPrincipal(managerId))).containsExactly(r);
  }

  @Test
  void listReviews_managerOfDifferentEmployee_throwsAccessDenied() {
    // Tightened authz: a peer manager (MANAGER role but not THIS plan's manager) cannot
    // read another team's review history.
    UUID planId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    UUID actualManager = UUID.randomUUID();
    UUID peerManager = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, owner, LocalDate.parse("2026-04-27"));
    Employee ownerRow = new Employee(owner, UUID.randomUUID());
    ownerRow.setManagerId(actualManager);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(employees.findById(owner)).thenReturn(Optional.of(ownerRow));

    assertThatThrownBy(() -> service().listReviews(planId, managerPrincipal(peerManager)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void listReviews_admin_returnsList() {
    // ADMIN bypasses the direct-manager check entirely.
    UUID planId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    ManagerReview r =
        new ManagerReview(UUID.randomUUID(), planId, UUID.randomUUID(), Instant.now());
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(reviews.findByPlanIdOrderByAcknowledgedAtAsc(planId)).thenReturn(List.of(r));

    assertThat(service().listReviews(planId, adminPrincipal())).containsExactly(r);
  }

  @Test
  void listReviews_nonOwnerIc_throwsAccessDenied() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));

    assertThatThrownBy(() -> service().listReviews(planId, icPrincipal(UUID.randomUUID())))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void listReviews_planNotFound_throwsResourceNotFound() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().listReviews(planId, icPrincipal(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);
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
