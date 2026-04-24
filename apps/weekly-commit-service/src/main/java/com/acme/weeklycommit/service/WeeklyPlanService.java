package com.acme.weeklycommit.service;

import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
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

  private static final Logger log = LoggerFactory.getLogger(WeeklyPlanService.class);

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
   * Read a specific {@code (employeeId, weekStart)} plan subject to authz:
   *
   * <ul>
   *   <li>Caller is the target employee (self).
   *   <li>Caller has the {@code MANAGER} role.
   * </ul>
   *
   * <p>Rejects with {@link AccessDeniedException} <b>before</b> hitting the DB so a peer's
   * existence cannot be probed via timing. v1 accepts that any MANAGER can read any employee;
   * tightening to "direct reports only" is deferred to group 9 rollup work.
   */
  @Transactional(readOnly = true)
  public Optional<WeeklyPlan> findPlan(
      UUID targetEmployeeId, LocalDate weekStart, AuthenticatedPrincipal caller) {
    if (!isSelfOrManager(targetEmployeeId, caller)) {
      throw new AccessDeniedException(
          "caller "
              + caller.employeeId()
              + " cannot read plan for employee "
              + targetEmployeeId);
    }
    return plans.findByEmployeeIdAndWeekStart(targetEmployeeId, weekStart);
  }

  private static boolean isSelfOrManager(UUID targetEmployeeId, AuthenticatedPrincipal caller) {
    return caller.employeeId().equals(targetEmployeeId) || caller.isManager();
  }

  /**
   * Idempotent create on {@code (employeeId, weekStart)}. If a plan already exists for the
   * caller's current week, return it unchanged. Otherwise save a new DRAFT plan.
   *
   * <p>Race handling: two concurrent callers can both see "no plan exists" and both attempt a
   * save. The DB {@code UNIQUE(employee_id, week_start)} constraint prevents duplicates; the
   * losing side here catches the {@link DataIntegrityViolationException} and re-fetches the
   * now-committed plan from the winning write. The caller never sees a 500.
   */
  @Transactional
  public WeeklyPlan createCurrentWeekPlan(AuthenticatedPrincipal caller) {
    LocalDate weekStart = currentWeekStartFor(caller);
    UUID employeeId = caller.employeeId();

    Optional<WeeklyPlan> existing = plans.findByEmployeeIdAndWeekStart(employeeId, weekStart);
    if (existing.isPresent()) {
      return existing.get();
    }

    WeeklyPlan draft = new WeeklyPlan(UUID.randomUUID(), employeeId, weekStart);
    try {
      return plans.save(draft);
    } catch (DataIntegrityViolationException raced) {
      log.warn(
          "createCurrentWeekPlan lost race for employee={} weekStart={}; re-fetching",
          employeeId,
          weekStart);
      return plans
          .findByEmployeeIdAndWeekStart(employeeId, weekStart)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "unique-violation on save but no plan visible on refetch — "
                          + "invariant broken for employee="
                          + employeeId
                          + " weekStart="
                          + weekStart,
                      raced));
    }
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
