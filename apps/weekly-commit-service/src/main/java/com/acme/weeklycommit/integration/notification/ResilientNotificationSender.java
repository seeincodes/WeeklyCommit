package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.domain.entity.NotificationDlt;
import com.acme.weeklycommit.repo.NotificationDltRepository;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Production-grade {@link NotificationSender}. Composes {@link NotificationClient} with
 * Resilience4j Retry + CircuitBreaker, and writes a {@link NotificationDlt} row when the call fails
 * after all attempts (or when the breaker is open). Never propagates exceptions: the dispatcher has
 * already committed the state-transition transaction, and a thrown exception here would echo a 5xx
 * back to a user whose LOCKED already succeeded.
 *
 * <p><b>Failure semantics by exception class:</b>
 *
 * <ul>
 *   <li>{@link NotificationValidationException} (400) — non-recoverable per ADR-0002. Log at WARN,
 *       no DLT (a retry won't fix bad request data; DLT-replaying it would just fail the same way
 *       and add noise).
 *   <li>{@link WebClientResponseException} after retries — write DLT with attempts and lastError.
 *       Admin can replay via {@code POST /admin/notifications/dlt/{id}/replay}.
 *   <li>{@link CallNotPermittedException} (circuit open) — write DLT immediately. The breaker is
 *       short-circuiting the call without an HTTP attempt.
 * </ul>
 *
 * <p><b>DLT payload contract:</b> {@code dlt.payload} is the JSON serialization of the original
 * {@link NotificationEvent} — pinned by {@code DltReplayService} in group 6. Drift on either side
 * breaks replay.
 */
public class ResilientNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(ResilientNotificationSender.class);

  private final NotificationClient client;
  private final NotificationDltRepository dltRepo;
  private final ObjectMapper objectMapper;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;

  public ResilientNotificationSender(
      NotificationClient client,
      NotificationDltRepository dltRepo,
      ObjectMapper objectMapper,
      Retry retry,
      CircuitBreaker circuitBreaker) {
    this.client = client;
    this.dltRepo = dltRepo;
    this.objectMapper = objectMapper;
    this.retry = retry;
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  public void send(NotificationEvent event) {
    // Manual decorator chain (no Decorators helper in resilience4j-core 2.2). Order matters:
    // CircuitBreaker on the inside means it sees individual attempts; Retry on the outside
    // re-fires the whole circuit-protected call. This matches the @Retry @CircuitBreaker
    // annotation default order in resilience4j-spring-boot3.
    Runnable cbProtected =
        CircuitBreaker.decorateRunnable(circuitBreaker, () -> client.send(event));
    Runnable decorated = Retry.decorateRunnable(retry, cbProtected);
    try {
      decorated.run();
    } catch (NotificationValidationException e) {
      log.warn(
          "notification dropped (validation, no retry no DLT) — plan={} {} -> {} v{}: {}",
          event.planId(),
          event.from(),
          event.to(),
          event.planVersion(),
          e.getMessage());
    } catch (CallNotPermittedException e) {
      writeDlt(event, "circuit-open: " + e.getMessage(), 0);
    } catch (WebClientResponseException e) {
      writeDlt(
          event,
          e.getStatusCode() + ": " + e.getMessage(),
          retry.getRetryConfig().getMaxAttempts());
    } catch (RuntimeException e) {
      // Defensive: anything else (timeout wrapped in a reactor exception, etc.) goes to DLT
      // rather than escaping. Log at ERROR with full stack so the unfamiliar shape is visible.
      log.error(
          "unexpected notification failure -- DLT'ing — plan={} {} -> {} v{}",
          event.planId(),
          event.from(),
          event.to(),
          event.planVersion(),
          e);
      writeDlt(event, e.getClass().getSimpleName() + ": " + e.getMessage(), 0);
    }
  }

  private void writeDlt(NotificationEvent event, String lastError, int attempts) {
    JsonNode payload = objectMapper.valueToTree(event);
    NotificationDlt row =
        new NotificationDlt(
            UUID.randomUUID(), "PLAN_" + event.to().name(), payload, lastError, attempts);
    dltRepo.save(row);
    log.warn(
        "notification DLT'd — plan={} {} -> {} v{} attempts={} error={}",
        event.planId(),
        event.from(),
        event.to(),
        event.planVersion(),
        attempts,
        lastError);
  }
}
