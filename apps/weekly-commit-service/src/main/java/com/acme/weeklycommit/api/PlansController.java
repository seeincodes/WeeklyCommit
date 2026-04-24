package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.WeeklyPlanMapper;
import com.acme.weeklycommit.api.dto.WeeklyPlanResponse;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.service.WeeklyPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for {@link WeeklyPlan}. Thin by design — validation + response shaping only.
 * Business logic, transactions, and authz live in {@link WeeklyPlanService} (per project memory:
 * authz at the service boundary, not scattered across controller annotations).
 *
 * <p>All endpoints emit the standard {@link ApiEnvelope} wrapper.
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlansController {

  private final WeeklyPlanService planService;
  private final WeeklyPlanMapper mapper;

  public PlansController(WeeklyPlanService planService, WeeklyPlanMapper mapper) {
    this.planService = planService;
    this.mapper = mapper;
  }

  /**
   * Current-week plan for the authenticated caller, or 404 if none exists. 404 drives the UI's
   * explicit "Start your week" blank state (MEMO decision #10: no auto-create on route entry).
   */
  @GetMapping("/me/current")
  public ResponseEntity<ApiEnvelope<WeeklyPlanResponse>> getCurrentForMe(
      AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        planService
            .findCurrentWeekPlan(caller)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "WeeklyPlan (current week for employee)", caller.employeeId()));
    return ResponseEntity.ok(ApiEnvelope.of(mapper.toResponse(plan)));
  }
}
