package com.acme.weeklycommit.integration.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.domain.entity.NotificationDlt;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.repo.NotificationDltRepository;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@ExtendWith(MockitoExtension.class)
class ResilientNotificationSenderTest {

  @Mock private NotificationClient client;
  @Mock private NotificationDltRepository dltRepo;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static NotificationEvent lockEvent() {
    return new NotificationEvent(
        UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
        PlanState.DRAFT,
        PlanState.LOCKED,
        3L);
  }

  /** Tight Retry config: 2 attempts, no backoff -- keeps the test fast. */
  private Retry retry() {
    return Retry.of(
        "test-notification",
        RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(WebClientResponseException.class)
            .build());
  }

  /** Tight CB config: tiny window, low threshold so the test doesn't have to flood requests. */
  private CircuitBreaker circuitBreaker() {
    return CircuitBreaker.of(
        "test-notification",
        CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build());
  }

  private ResilientNotificationSender sender() {
    return new ResilientNotificationSender(client, dltRepo, objectMapper, retry(), circuitBreaker());
  }

  @Test
  void send_happyPath_callsClientOnce_noDlt() {
    NotificationEvent event = lockEvent();
    doNothing().when(client).send(event);

    sender().send(event);

    verify(client, times(1)).send(event);
    verify(dltRepo, never()).save(any());
  }

  @Test
  void send_5xxRetriesUntilSuccess_noDlt() {
    NotificationEvent event = lockEvent();
    // First call throws 503, second call succeeds.
    doThrow(WebClientResponseException.create(503, "Service Unavailable", null, null, null))
        .doNothing()
        .when(client)
        .send(event);

    sender().send(event);

    verify(client, times(2)).send(event);
    verify(dltRepo, never()).save(any());
  }

  @Test
  void send_5xxRetriesExhausted_writesDlt() {
    NotificationEvent event = lockEvent();
    doThrow(WebClientResponseException.create(503, "Service Unavailable", null, null, null))
        .when(client)
        .send(event);
    when(dltRepo.save(any(NotificationDlt.class))).thenAnswer(inv -> inv.getArgument(0));

    sender().send(event);

    verify(client, times(2)).send(event);
    ArgumentCaptor<NotificationDlt> captor = ArgumentCaptor.forClass(NotificationDlt.class);
    verify(dltRepo).save(captor.capture());
    NotificationDlt row = captor.getValue();
    assertThat(row.getEventType()).isEqualTo("PLAN_LOCKED");
    assertThat(row.getAttempts()).isEqualTo(2);
    assertThat(row.getLastError()).contains("503");
    // Payload is the JSON serialization of the NotificationEvent (DltReplayService contract).
    assertThat(row.getPayload().get("planId").asText()).isEqualTo(event.planId().toString());
    assertThat(row.getPayload().get("to").asText()).isEqualTo("LOCKED");
    assertThat(row.getPayload().get("planVersion").asLong()).isEqualTo(3L);
  }

  @Test
  void send_400ValidationException_noRetry_noDlt() {
    NotificationEvent event = lockEvent();
    doThrow(new NotificationValidationException("bad templateVars")).when(client).send(event);

    sender().send(event);

    // Validation errors are non-recoverable per ADR-0002: retry burns budget, DLT accumulates
    // noise. Single attempt; nothing saved.
    verify(client, times(1)).send(event);
    verify(dltRepo, never()).save(any());
  }

  @Test
  void send_doesNotPropagateExceptions_keepsTransitionFromUnwinding() {
    // The state-transition transaction has already committed when the dispatcher fires
    // (TransactionAwareNotificationDispatcher's afterCommit). If send() threw here, the user
    // would see their transition succeed but get a 5xx echo. Always swallow + DLT.
    NotificationEvent event = lockEvent();
    doThrow(WebClientResponseException.create(503, "down", null, null, null))
        .when(client)
        .send(event);

    sender().send(event); // must not throw
    verify(dltRepo, atLeast(1)).save(any(NotificationDlt.class));
  }
}
