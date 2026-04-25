package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.api.dto.CreateCommitRequest;
import com.acme.weeklycommit.api.exception.InvalidStateTransitionException;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.math.BigDecimal;
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
class WeeklyCommitServiceTest {

  @Mock private WeeklyPlanRepository plans;
  @Mock private WeeklyCommitRepository commits;

  private WeeklyCommitService service() {
    return new WeeklyCommitService(plans, commits);
  }

  @Test
  void findCommitsForPlan_selfOwner_returnsOrderedList() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    WeeklyCommit c1 =
        new WeeklyCommit(UUID.randomUUID(), planId, "a", UUID.randomUUID(), ChessTier.ROCK, 0);
    WeeklyCommit c2 =
        new WeeklyCommit(UUID.randomUUID(), planId, "b", UUID.randomUUID(), ChessTier.PEBBLE, 1);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(planId)).thenReturn(List.of(c1, c2));

    List<WeeklyCommit> result = service().findCommitsForPlan(planId, principal(employeeId));

    assertThat(result).containsExactly(c1, c2);
  }

  @Test
  void findCommitsForPlan_manager_canReadAnyPlansCommits() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    WeeklyCommit c =
        new WeeklyCommit(UUID.randomUUID(), planId, "x", UUID.randomUUID(), ChessTier.SAND, 0);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(planId)).thenReturn(List.of(c));

    assertThat(service().findCommitsForPlan(planId, managerPrincipal(UUID.randomUUID())))
        .containsExactly(c);
  }

  @Test
  void findCommitsForPlan_nonOwnerIc_throwsAccessDenied_commitsNeverLoaded() {
    UUID planId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, UUID.randomUUID(), LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    AuthenticatedPrincipal stranger = principal(UUID.randomUUID());

    assertThatThrownBy(() -> service().findCommitsForPlan(planId, stranger))
        .isInstanceOf(AccessDeniedException.class);

    verify(commits, never()).findByPlanIdOrderByDisplayOrderAsc(any());
  }

  @Test
  void findCommitsForPlan_planNotFound_throwsResourceNotFound() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().findCommitsForPlan(planId, principal(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(commits, never()).findByPlanIdOrderByDisplayOrderAsc(any());
  }

  // --- createCommit (POST /plans/{planId}/commits) ---

  @Test
  void createCommit_draftPlanOwner_savesCommit() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    UUID outcomeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    // plan.state defaults to DRAFT
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(planId)).thenReturn(List.of());
    when(commits.save(any(WeeklyCommit.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateCommitRequest req =
        new CreateCommitRequest(
            "land RCDO spike",
            "description text",
            outcomeId,
            ChessTier.ROCK,
            List.of("spike", "infra"),
            new BigDecimal("4.5"),
            null, // displayOrder auto-assigned
            "Tues 10am sync");

    WeeklyCommit result = service().createCommit(planId, req, principal(employeeId));

    ArgumentCaptor<WeeklyCommit> captor = ArgumentCaptor.forClass(WeeklyCommit.class);
    verify(commits).save(captor.capture());
    WeeklyCommit saved = captor.getValue();
    assertThat(saved.getPlanId()).isEqualTo(planId);
    assertThat(saved.getTitle()).isEqualTo("land RCDO spike");
    assertThat(saved.getDescription()).isEqualTo("description text");
    assertThat(saved.getSupportingOutcomeId()).isEqualTo(outcomeId);
    assertThat(saved.getChessTier()).isEqualTo(ChessTier.ROCK);
    assertThat(saved.getCategoryTags()).containsExactly("spike", "infra");
    assertThat(saved.getEstimatedHours()).isEqualByComparingTo("4.5");
    assertThat(saved.getDisplayOrder()).isEqualTo(0); // first, since list empty
    assertThat(saved.getRelatedMeeting()).isEqualTo("Tues 10am sync");
    assertThat(result).isSameAs(saved);
  }

  @Test
  void createCommit_displayOrder_autoAssignedAsNext() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    WeeklyCommit existing1 =
        new WeeklyCommit(UUID.randomUUID(), planId, "a", UUID.randomUUID(), ChessTier.ROCK, 0);
    WeeklyCommit existing2 =
        new WeeklyCommit(UUID.randomUUID(), planId, "b", UUID.randomUUID(), ChessTier.PEBBLE, 1);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(commits.findByPlanIdOrderByDisplayOrderAsc(planId))
        .thenReturn(List.of(existing1, existing2));
    when(commits.save(any(WeeklyCommit.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateCommitRequest req =
        new CreateCommitRequest(
            "c", null, UUID.randomUUID(), ChessTier.SAND, null, null, null, null);

    WeeklyCommit result = service().createCommit(planId, req, principal(employeeId));

    assertThat(result.getDisplayOrder()).isEqualTo(2); // next after 0, 1
  }

  @Test
  void createCommit_displayOrder_honoredWhenProvided() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    when(commits.save(any(WeeklyCommit.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateCommitRequest req =
        new CreateCommitRequest(
            "x", null, UUID.randomUUID(), ChessTier.ROCK, null, null, 7, null);

    WeeklyCommit result = service().createCommit(planId, req, principal(employeeId));

    assertThat(result.getDisplayOrder()).isEqualTo(7);
  }

  @Test
  void createCommit_nonOwner_throwsAccessDenied_evenIfManager() {
    // Owner-only; managers review, they don't create commits for others.
    UUID planId = UUID.randomUUID();
    UUID planOwnerId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, planOwnerId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));

    CreateCommitRequest req =
        new CreateCommitRequest(
            "x", null, UUID.randomUUID(), ChessTier.ROCK, null, null, null, null);

    assertThatThrownBy(
            () ->
                service()
                    .createCommit(planId, req, managerPrincipal(UUID.randomUUID())))
        .isInstanceOf(AccessDeniedException.class);

    verify(commits, never()).save(any(WeeklyCommit.class));
  }

  @Test
  void createCommit_lockedPlan_rejected() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    plan.setState(PlanState.LOCKED);
    when(plans.findById(planId)).thenReturn(Optional.of(plan));

    CreateCommitRequest req =
        new CreateCommitRequest(
            "x", null, UUID.randomUUID(), ChessTier.ROCK, null, null, null, null);

    assertThatThrownBy(() -> service().createCommit(planId, req, principal(employeeId)))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("DRAFT");

    verify(commits, never()).save(any(WeeklyCommit.class));
  }

  @Test
  void createCommit_planNotFound_throwsResourceNotFound() {
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    CreateCommitRequest req =
        new CreateCommitRequest(
            "x", null, UUID.randomUUID(), ChessTier.ROCK, null, null, null, null);

    assertThatThrownBy(
            () -> service().createCommit(planId, req, principal(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // --- helpers ---

  private static AuthenticatedPrincipal principal(UUID employeeId) {
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
}
