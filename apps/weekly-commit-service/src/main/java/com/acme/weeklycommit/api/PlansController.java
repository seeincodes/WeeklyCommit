package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.TransitionRequest;
import com.acme.weeklycommit.api.dto.UpdateReflectionRequest;
import com.acme.weeklycommit.api.dto.WeeklyPlanMapper;
import com.acme.weeklycommit.api.dto.WeeklyPlanResponse;
import com.acme.weeklycommit.api.exception.PageSizeExceededException;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.service.WeeklyPlanService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /**
   * Create the caller's current-week plan (DRAFT). Idempotent on {@code (employeeId, weekStart)}:
   * returning 201 regardless of whether a new row was inserted or a pre-existing plan was returned.
   * The client doesn't need to distinguish — per USER_FLOW.md the response shape is identical.
   */
  @PostMapping
  public ResponseEntity<ApiEnvelope<WeeklyPlanResponse>> createCurrentForMe(
      AuthenticatedPrincipal caller) {
    WeeklyPlan plan = planService.createCurrentWeekPlan(caller);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(mapper.toResponse(plan)));
  }

  /**
   * Look up a specific plan by {@code (employeeId, weekStart)}. Authz decided by the service: self
   * or MANAGER, else 403. 404 on missing plan.
   */
  @GetMapping
  public ResponseEntity<ApiEnvelope<WeeklyPlanResponse>> getPlanByEmployeeAndWeek(
      @RequestParam("employeeId") UUID employeeId,
      @RequestParam("weekStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        planService
            .findPlan(employeeId, weekStart, caller)
            .orElseThrow(() -> new ResourceNotFoundException("WeeklyPlan", employeeId));
    return ResponseEntity.ok(ApiEnvelope.of(mapper.toResponse(plan)));
  }

  /**
   * Trigger a lifecycle transition. Owner-only authz (enforced by the service). Body shape is
   * {@link TransitionRequest}. Invalid targets produce 422 via {@code
   * InvalidStateTransitionException} from the state machine.
   */
  @PostMapping("/{planId}/transitions")
  public ResponseEntity<ApiEnvelope<WeeklyPlanResponse>> transition(
      @PathVariable UUID planId,
      @Valid @RequestBody TransitionRequest request,
      AuthenticatedPrincipal caller) {
    WeeklyPlan plan = planService.transitionPlan(planId, request.to(), caller);
    return ResponseEntity.ok(ApiEnvelope.of(mapper.toResponse(plan)));
  }

  /**
   * Update the plan's reflection note. Owner-only (service enforces); only valid in
   * reconciliation mode (LOCKED past day-4). Null {@code reflectionNote} is accepted and clears
   * the field.
   */
  @PatchMapping("/{planId}")
  public ResponseEntity<ApiEnvelope<WeeklyPlanResponse>> updateReflection(
      @PathVariable UUID planId,
      @Valid @RequestBody UpdateReflectionRequest request,
      AuthenticatedPrincipal caller) {
    WeeklyPlan plan =
        planService.updateReflectionNote(planId, request.reflectionNote(), caller);
    return ResponseEntity.ok(ApiEnvelope.of(mapper.toResponse(plan)));
  }

  /** Server-side cap on page size (per USER_FLOW.md). */
  private static final int MAX_PAGE_SIZE = 100;

  /** Default page size when the caller doesn't supply one. */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /**
   * Manager team view for a given week. Authz: caller must be {@code managerId} themselves or
   * hold the {@code ADMIN} role (skip-level). Page size is capped at {@link #MAX_PAGE_SIZE};
   * larger values produce 400 rather than being silently clamped (explicit > magic).
   *
   * <p>Response envelope:
   * <ul>
   *   <li>{@code data} — array of {@link WeeklyPlanResponse}
   *   <li>{@code meta.page}, {@code meta.size}, {@code meta.totalElements},
   *       {@code meta.totalPages} — standard paging fields, mirroring the shape the frontend
   *       expects without leaking Spring's verbose Page serialization.
   * </ul>
   */
  @GetMapping("/team")
  public ResponseEntity<ApiEnvelope<List<WeeklyPlanResponse>>> getTeam(
      @RequestParam("managerId") UUID managerId,
      @RequestParam("weekStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
      AuthenticatedPrincipal caller) {
    if (size > MAX_PAGE_SIZE) {
      throw new PageSizeExceededException(size, MAX_PAGE_SIZE);
    }
    Pageable pageable = PageRequest.of(page, size);
    Page<WeeklyPlan> result = planService.findTeamPlans(managerId, weekStart, pageable, caller);

    List<WeeklyPlanResponse> items =
        result.getContent().stream().map(mapper::toResponse).toList();

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("now", Instant.now().toString());
    meta.put("page", result.getNumber());
    meta.put("size", result.getSize());
    meta.put("totalElements", result.getTotalElements());
    meta.put("totalPages", result.getTotalPages());

    return ResponseEntity.ok(new ApiEnvelope<>(items, meta));
  }
}
