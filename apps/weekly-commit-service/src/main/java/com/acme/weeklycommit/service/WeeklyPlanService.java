package com.acme.weeklycommit.service;

import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write operations against {@code WeeklyPlan} aggregated for the API layer.
 *
 * <p>Authz is enforced here (service boundary) per the project's code-quality memo — callers hand
 * an {@link AuthenticatedPrincipal} in, and the service decides which plans they may see or
 * mutate. Controllers remain thin (validation + response shaping).
 *
 * <p>Week math is TZ-aware per presearch A4: the week start is the Monday-of-current-week in the
 * caller's IANA timezone. Storage is a {@code DATE} column with no zone; callers should not
 * interpret the date outside the context of the employee's profile timezone.
 */
@Service
public class WeeklyPlanService {

  private final WeeklyPlanRepository plans;
  private final Clock clock;

  public WeeklyPlanService(WeeklyPlanRepository plans, Clock clock) {
    this.plans = plans;
    this.clock = clock;
  }

  /**
   * Returns the caller's current-week plan, or empty if none exists.
   *
   * <p>Self-scope: the employee id is read from the {@link AuthenticatedPrincipal} — there is no
   * overload that accepts a different employee id, so this endpoint can never leak a peer's plan.
   */
  @Transactional(readOnly = true)
  public Optional<WeeklyPlan> findCurrentWeekPlan(AuthenticatedPrincipal caller) {
    LocalDate weekStart = currentWeekStartFor(caller);
    return plans.findByEmployeeIdAndWeekStart(caller.employeeId(), weekStart);
  }

  /**
   * Monday of the week that contains "now" from the caller's perspective. Computed in the
   * caller's IANA zone so a Tokyo employee's Monday and a Los Angeles employee's Monday aren't
   * muddled by UTC arithmetic. Private — callers that need this should go through {@link
   * #findCurrentWeekPlan}.
   */
  private LocalDate currentWeekStartFor(AuthenticatedPrincipal caller) {
    return LocalDate.now(clock.withZone(caller.timezone()))
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }
}
