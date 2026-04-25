package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanServiceTest {

  @Mock private WeeklyPlanRepository plans;
  @Mock private com.acme.weeklycommit.service.statemachine.WeeklyPlanStateMachine stateMachine;

  private WeeklyPlanService service(Clock clock) {
    return new WeeklyPlanService(plans, stateMachine, clock);
  }

  @Test
  void findCurrentWeekPlan_utcCaller_resolvesMondayInUtc() {
    // Caller in UTC; now = Wednesday 2026-04-29T10:00Z -> currentWeek starts 2026-04-27.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("UTC"));
    WeeklyPlan match = new WeeklyPlan(UUID.randomUUID(), employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.of(match));

    Optional<WeeklyPlan> result = service(clock).findCurrentWeekPlan(caller);

    assertThat(result).contains(match);
  }

  @Test
  void findCurrentWeekPlan_tzBeforeUtc_monday_resolvesToLocalMonday() {
    // Caller in America/Los_Angeles. Monday 2026-04-27 00:30 PT = Monday 2026-04-27 07:30 UTC.
    // In PT it's still Monday 2026-04-27; weekStart should be 2026-04-27, not 2026-04-20.
    Clock clock = Clock.fixed(Instant.parse("2026-04-27T07:30:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("America/Los_Angeles"));
    WeeklyPlan match = new WeeklyPlan(UUID.randomUUID(), employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.of(match));

    Optional<WeeklyPlan> result = service(clock).findCurrentWeekPlan(caller);

    assertThat(result).contains(match);
  }

  @Test
  void findCurrentWeekPlan_tzAheadOfUtc_earlyMonday_resolvesToLocalMonday() {
    // Caller in Asia/Tokyo. Sunday 2026-05-03 18:00 UTC = Monday 2026-05-04 03:00 JST.
    // In Tokyo the week already rolled over; weekStart should be 2026-05-04.
    Clock clock = Clock.fixed(Instant.parse("2026-05-03T18:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("Asia/Tokyo"));
    WeeklyPlan match = new WeeklyPlan(UUID.randomUUID(), employeeId, LocalDate.parse("2026-05-04"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-05-04")))
        .thenReturn(Optional.of(match));

    Optional<WeeklyPlan> result = service(clock).findCurrentWeekPlan(caller);

    assertThat(result).contains(match);
  }

  @Test
  void findCurrentWeekPlan_noPlanExists_returnsEmpty() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.empty());

    Optional<WeeklyPlan> result =
        service(clock).findCurrentWeekPlan(principal(employeeId, ZoneId.of("UTC")));

    assertThat(result).isEmpty();
  }

  // --- createCurrentWeekPlan ---

  @Test
  void createCurrentWeekPlan_whenNoPlanExists_savesDraft() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("UTC"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.empty());
    when(plans.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    WeeklyPlan result = service(clock).createCurrentWeekPlan(caller);

    ArgumentCaptor<WeeklyPlan> captor = ArgumentCaptor.forClass(WeeklyPlan.class);
    verify(plans).save(captor.capture());
    WeeklyPlan saved = captor.getValue();
    assertThat(saved.getEmployeeId()).isEqualTo(employeeId);
    assertThat(saved.getWeekStart()).isEqualTo(LocalDate.parse("2026-04-27"));
    assertThat(saved.getState()).isEqualTo(PlanState.DRAFT);
    assertThat(result).isSameAs(saved);
  }

  @Test
  void createCurrentWeekPlan_idempotent_whenPlanAlreadyExists_returnsExisting() {
    // POST /plans is idempotent on (employeeId, weekStart) per MEMO #10.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("UTC"));
    WeeklyPlan existing =
        new WeeklyPlan(UUID.randomUUID(), employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.of(existing));

    WeeklyPlan result = service(clock).createCurrentWeekPlan(caller);

    assertThat(result).isSameAs(existing);
    verify(plans, never()).save(any(WeeklyPlan.class));
  }

  @Test
  void createCurrentWeekPlan_raceCondition_recoversViaRefetch() {
    // Two concurrent callers both see empty on findBy..., both try to save. The second's save
    // hits the UNIQUE(employee_id, week_start) constraint. The service must recover by
    // re-fetching rather than surfacing the constraint violation to the caller.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("UTC"));
    WeeklyPlan racedIn =
        new WeeklyPlan(UUID.randomUUID(), employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.empty()) // first check (pre-save)
        .thenReturn(Optional.of(racedIn)); // post-violation refetch
    when(plans.save(any(WeeklyPlan.class)))
        .thenThrow(new DataIntegrityViolationException("unique violation"));

    WeeklyPlan result = service(clock).createCurrentWeekPlan(caller);

    assertThat(result).isSameAs(racedIn);
  }

  // --- findPlan (GET /plans) ---

  @Test
  void findPlan_selfAccess_returnsPlanWhenExists() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("UTC"));
    WeeklyPlan match = new WeeklyPlan(UUID.randomUUID(), employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.of(match));

    Optional<WeeklyPlan> result =
        service(clock).findPlan(employeeId, LocalDate.parse("2026-04-27"), caller);

    assertThat(result).contains(match);
  }

  @Test
  void findPlan_selfAccess_returnsEmptyWhenNoPlan() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("UTC"));
    when(plans.findByEmployeeIdAndWeekStart(employeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.empty());

    assertThat(service(clock).findPlan(employeeId, LocalDate.parse("2026-04-27"), caller))
        .isEmpty();
  }

  @Test
  void findPlan_manager_canReadAnyEmployeesPlan() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID managerId = UUID.randomUUID();
    UUID targetEmployeeId = UUID.randomUUID();
    AuthenticatedPrincipal manager = managerPrincipal(managerId);
    WeeklyPlan match =
        new WeeklyPlan(UUID.randomUUID(), targetEmployeeId, LocalDate.parse("2026-04-27"));
    when(plans.findByEmployeeIdAndWeekStart(targetEmployeeId, LocalDate.parse("2026-04-27")))
        .thenReturn(Optional.of(match));

    Optional<WeeklyPlan> result =
        service(clock).findPlan(targetEmployeeId, LocalDate.parse("2026-04-27"), manager);

    assertThat(result).contains(match);
  }

  @Test
  void findPlan_nonManagerReadingPeer_throwsAccessDenied() {
    // Security invariant: an IC cannot read another IC's plan.
    Clock clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneId.of("UTC"));
    UUID callerId = UUID.randomUUID();
    UUID peerEmployeeId = UUID.randomUUID();
    AuthenticatedPrincipal ic = principal(callerId, ZoneId.of("UTC"));

    assertThatThrownBy(
            () -> service(clock).findPlan(peerEmployeeId, LocalDate.parse("2026-04-27"), ic))
        .isInstanceOf(AccessDeniedException.class);

    verify(plans, never()).findByEmployeeIdAndWeekStart(any(), any());
  }

  // --- transitionPlan (POST /plans/{id}/transitions) ---

  @Test
  void transitionPlan_selfCaller_delegatesToStateMachine() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-27T18:00:00Z"), ZoneId.of("UTC"));
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(employeeId, ZoneId.of("UTC"));
    WeeklyPlan plan = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));
    WeeklyPlan transitioned = new WeeklyPlan(planId, employeeId, LocalDate.parse("2026-04-27"));
    transitioned.setState(PlanState.LOCKED);
    when(stateMachine.transition(planId, PlanState.LOCKED, employeeId)).thenReturn(transitioned);

    WeeklyPlan result = service(clock).transitionPlan(planId, PlanState.LOCKED, caller);

    assertThat(result.getState()).isEqualTo(PlanState.LOCKED);
    verify(stateMachine).transition(planId, PlanState.LOCKED, employeeId);
  }

  @Test
  void transitionPlan_otherEmployeesPlan_throwsAccessDenied_stateMachineNeverCalled() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-27T18:00:00Z"), ZoneId.of("UTC"));
    UUID planId = UUID.randomUUID();
    UUID planOwnerId = UUID.randomUUID();
    UUID differentCallerId = UUID.randomUUID();
    WeeklyPlan plan = new WeeklyPlan(planId, planOwnerId, LocalDate.parse("2026-04-27"));
    when(plans.findById(planId)).thenReturn(Optional.of(plan));

    // Even a manager cannot transition someone else's plan — transitions are owner-only
    // (managers review, they don't act on the IC's behalf).
    AuthenticatedPrincipal managerCaller = managerPrincipal(differentCallerId);

    assertThatThrownBy(() -> service(clock).transitionPlan(planId, PlanState.LOCKED, managerCaller))
        .isInstanceOf(AccessDeniedException.class);

    verify(stateMachine, never()).transition(any(), any(), any());
  }

  @Test
  void transitionPlan_planNotFound_throwsResourceNotFound() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-27T18:00:00Z"), ZoneId.of("UTC"));
    UUID planId = UUID.randomUUID();
    when(plans.findById(planId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service(clock)
                    .transitionPlan(
                        planId, PlanState.LOCKED, principal(UUID.randomUUID(), ZoneId.of("UTC"))))
        .isInstanceOf(com.acme.weeklycommit.api.exception.ResourceNotFoundException.class);

    verify(stateMachine, never()).transition(any(), any(), any());
  }

  // --- helpers ---

  private static AuthenticatedPrincipal managerPrincipal(UUID employeeId) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", employeeId.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", "UTC",
                "roles", java.util.List.of("MANAGER")));
    return new AuthenticatedPrincipal(
        employeeId, UUID.randomUUID(), Optional.empty(), Set.of("MANAGER"), ZoneId.of("UTC"), jwt);
  }

  private static AuthenticatedPrincipal principal(UUID employeeId, ZoneId tz) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", employeeId.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", tz.getId()));
    return new AuthenticatedPrincipal(
        employeeId, UUID.randomUUID(), Optional.empty(), Set.of(), tz, jwt);
  }
}
