package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.UnassignedEmployeeResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.service.AdminEmployeeService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

  public AdminController(AdminEmployeeService adminEmployeeService) {
    this.adminEmployeeService = adminEmployeeService;
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
}
