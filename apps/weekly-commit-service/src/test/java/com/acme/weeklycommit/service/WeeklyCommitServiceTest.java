package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
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
