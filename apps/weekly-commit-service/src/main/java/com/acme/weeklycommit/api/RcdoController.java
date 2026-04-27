package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.SupportingOutcomeResponse;
import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.integration.rcdo.RcdoClient;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pass-through layer between the UI and the upstream RCDO service. The UI cannot call RCDO directly
 * (CORS + service-token), and MEMO #6 designates the backend as the broker. This controller wraps
 * the existing {@link RcdoClient} so the integration layer keeps its own contract separate from the
 * UI's.
 *
 * <p>Both endpoints are scoped to the JWT's {@code org_id} -- an IC cannot query another org. The
 * single-id endpoint is unscoped because Supporting Outcome ids are globally unique and the picker
 * may need to hydrate an outcome that came from a previous-org assignment (rare but possible).
 *
 * <p>Errors:
 *
 * <ul>
 *   <li>{@code Optional.empty()} from {@link RcdoClient#findSupportingOutcome(UUID)} -> 404
 *       (handled by the global {@link com.acme.weeklycommit.api.exception.GlobalExceptionHandler}).
 *   <li>5xx from upstream propagates as a {@code WebClientResponseException}, mapped to 502 BAD
 *       GATEWAY by the global handler so the UI's `<RCDOPicker isStale />` banner can surface.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/rcdo")
public class RcdoController {

  private final RcdoClient rcdoClient;

  public RcdoController(RcdoClient rcdoClient) {
    this.rcdoClient = rcdoClient;
  }

  @GetMapping("/supporting-outcomes")
  public ResponseEntity<ApiEnvelope<List<SupportingOutcomeResponse>>> list(
      AuthenticatedPrincipal caller) {
    List<SupportingOutcomeResponse> outcomes =
        rcdoClient.findActiveSupportingOutcomes(caller.organizationId()).stream()
            .map(SupportingOutcomeResponse::from)
            .toList();
    return ResponseEntity.ok(ApiEnvelope.of(outcomes));
  }

  @GetMapping("/supporting-outcomes/{id}")
  public ResponseEntity<ApiEnvelope<SupportingOutcomeResponse>> getById(@PathVariable UUID id) {
    SupportingOutcomeResponse outcome =
        rcdoClient
            .findSupportingOutcome(id)
            .map(SupportingOutcomeResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("SupportingOutcome", id));
    return ResponseEntity.ok(ApiEnvelope.of(outcome));
  }
}
