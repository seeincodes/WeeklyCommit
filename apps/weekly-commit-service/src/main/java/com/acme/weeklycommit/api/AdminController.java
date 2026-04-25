package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.UnassignedEmployeeResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.service.AdminEmployeeService;
import com.acme.weeklycommit.service.DltReplayService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only operational surface. The {@code /api/v1/admin/**} prefix is gated to {@code
 * hasRole("ADMIN")} in {@link com.acme.weeklycommit.config.SecurityConfig}, so unauthorized callers
 * are rejected at the filter chain. The service layer also enforces ADMIN as defense-in-depth.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final AdminEmployeeService adminEmployeeService;
  private final DltReplayService dltReplayService;

  public AdminController(
      AdminEmployeeService adminEmployeeService, DltReplayService dltReplayService) {
    this.adminEmployeeService = adminEmployeeService;
    this.dltReplayService = dltReplayService;
  }

  @GetMapping("/unassigned-employees")
  public ResponseEntity<ApiEnvelope<List<UnassignedEmployeeResponse>>> listUnassignedEmployees(
      AuthenticatedPrincipal caller) {
    List<UnassignedEmployeeResponse> body =
        adminEmployeeService.listUnassignedEmployees(caller).stream()
            .map(AdminController::toResponse)
            .toList();
    return ResponseEntity.ok(ApiEnvelope.of(body));
  }

  private static UnassignedEmployeeResponse toResponse(Employee e) {
    return new UnassignedEmployeeResponse(e.getId(), e.getDisplayName(), e.getLastSyncedAt());
  }

  /**
   * Replay a single dead-letter notification row. Synchronous send-and-delete; returns 202 to keep
   * the door open for an async migration in v2. Body is intentionally empty — the only outcome
   * worth communicating is success vs failure, captured by the status code.
   */
  @PostMapping("/notifications/dlt/{id}/replay")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
  public void replayDltRow(@PathVariable("id") UUID id, AuthenticatedPrincipal caller) {
    dltReplayService.replay(id, caller);
  }
}
