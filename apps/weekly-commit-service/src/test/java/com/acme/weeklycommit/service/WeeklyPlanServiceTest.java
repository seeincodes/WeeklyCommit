package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanServiceTest {

  @Mock private WeeklyPlanRepository plans;

  private WeeklyPlanService service(Clock clock) {
    return new WeeklyPlanService(plans, clock);
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

  // --- helpers ---

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
