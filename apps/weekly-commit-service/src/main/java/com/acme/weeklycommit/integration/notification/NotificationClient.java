package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Raw HTTP wrapper around notification-svc. Owns request shape and the few status-code mappings
 * that ADR-0002 calls out as non-default behavior. Resilience4j and DLT logic live in {@link
 * ResilientNotificationSender}, which composes this client.
 *
 * <p><b>v1 deferral:</b> notification-svc resolves the recipient internally from {@code
 * metadata.planId} rather than us passing {@code recipientEmployeeId}. This is a deliberate v1-stub
 * departure from ADR-0002 — threading employee lookups through the state machine for every
 * transition adds enough surface to delay the foundational integration work for no functional
 * benefit while the contract is still stubbed. Revisit when notification-svc is real.
 */
public class NotificationClient {

  private static final String SOURCE_SERVICE = "weekly-commit-service";

  private final WebClient webClient;
  private final Duration timeout;

  public NotificationClient(WebClient webClient, Duration timeout) {
    this.webClient = webClient;
    this.timeout = timeout;
  }

  /**
   * POST a single notification corresponding to a state transition. Returns normally on 202
   * (queued) and 409 (duplicate idempotency key — treated as success per ADR-0002). 400 is mapped
   * to {@link NotificationValidationException} so the caller can skip both retry and DLT. 5xx and
   * 401 propagate as {@link WebClientResponseException} so the caller's retry/DLT layer can apply.
   *
   * <p>If the event corresponds to a transition we don't notify on (e.g. ARCHIVED), this method is
   * a no-op — saves a wasted HTTP call and matches the ADR's "v1 events" list.
   */
  public void send(NotificationEvent event) {
    Optional<EventMapping> mapping = mapEvent(event);
    if (mapping.isEmpty()) {
      return; // unsubscribed event type for v1; ADR-0002 limits to LOCKED + RECONCILED
    }
    EventMapping m = mapping.get();
    String idempotencyKey =
        "wc-plan-" + event.planId() + "-" + event.to() + "-v" + event.planVersion();

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("eventType", m.eventType);
    body.put("channel", "EMAIL");
    body.put("templateId", m.templateId);
    body.put(
        "templateVars",
        Map.of(
            "planId", event.planId().toString(),
            "fromState", event.from().name(),
            "toState", event.to().name(),
            "planVersion", event.planVersion()));
    body.put(
        "metadata", Map.of("sourceService", SOURCE_SERVICE, "planId", event.planId().toString()));

    try {
      webClient
          .post()
          .uri("/notifications/send")
          .header("X-Idempotency-Key", idempotencyKey)
          .bodyValue(body)
          .retrieve()
          .toBodilessEntity()
          .block(timeout);
    } catch (WebClientResponseException e) {
      HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
      if (status == HttpStatus.CONFLICT) {
        return; // duplicate idempotency key == already delivered
      }
      if (status == HttpStatus.BAD_REQUEST) {
        throw new NotificationValidationException(
            "notification-svc rejected payload as invalid: " + e.getResponseBodyAsString());
      }
      throw e;
    }
  }

  /**
   * Map a transition to its template + event-type pair. {@link Optional#empty()} for transitions
   * that don't trigger a notification in v1 (per ADR-0002).
   */
  private static Optional<EventMapping> mapEvent(NotificationEvent event) {
    if (event.to() == PlanState.LOCKED) {
      return Optional.of(new EventMapping("WEEKLY_PLAN_LOCKED", "weekly-commit.plan-locked.v1"));
    }
    if (event.to() == PlanState.RECONCILED) {
      return Optional.of(
          new EventMapping(
              "WEEKLY_PLAN_RECONCILED_TO_MANAGER", "weekly-commit.reconciled-to-manager.v1"));
    }
    return Optional.empty();
  }

  private record EventMapping(String eventType, String templateId) {}
}
