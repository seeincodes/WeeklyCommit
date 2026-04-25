package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.AuditLogResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.service.AuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only audit history. Single endpoint per USER_FLOW.md: {@code GET /audit/plans/{id}} returns
 * the full transition + manager-review history of a plan to its IC, the IC's MANAGER, or ADMIN.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

  private final AuditService auditService;

  public AuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping("/plans/{id}")
  public ResponseEntity<ApiEnvelope<List<AuditLogResponse>>> getAuditForPlan(
      @PathVariable("id") UUID id, AuthenticatedPrincipal caller) {
    List<AuditLogResponse> body =
        auditService.findForPlan(id, caller).stream().map(AuditController::toResponse).toList();
    return ResponseEntity.ok(ApiEnvelope.of(body));
  }

  private static AuditLogResponse toResponse(AuditLog row) {
    return new AuditLogResponse(
        row.getId(),
        row.getEntityType(),
        row.getEntityId(),
        row.getEventType(),
        row.getActorId(),
        row.getFromState(),
        row.getToState(),
        row.getMetadata(),
        row.getOccurredAt());
  }
}
