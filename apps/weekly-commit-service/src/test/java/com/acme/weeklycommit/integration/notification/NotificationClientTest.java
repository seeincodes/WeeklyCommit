package com.acme.weeklycommit.integration.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Contract tests for {@link NotificationClient} against a WireMock-stubbed notification-svc. Pins
 * the request/response shape from docs/adr/0002-notification-svc-contract.md.
 *
 * <p>Status-code semantics covered here: 202 (queued, success), 400 (validation, no retry no DLT),
 * 409 (duplicate idempotency key, treated as success). Retry-able statuses (503, 429) are not
 * exercised here -- they propagate as {@link WebClientResponseException}, and the {@link
 * ResilientNotificationSender} wrapper drives retry + DLT semantics in its own test.
 */
@WireMockTest
class NotificationClientTest {

  private NotificationClient client(WireMockRuntimeInfo info) {
    WebClient webClient =
        WebClient.builder()
            .baseUrl(info.getHttpBaseUrl())
            .defaultHeader("Authorization", "Bearer test-token")
            .build();
    return new NotificationClient(webClient, Duration.ofSeconds(2));
  }

  private static NotificationEvent lockEvent() {
    UUID planId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    return new NotificationEvent(planId, PlanState.DRAFT, PlanState.LOCKED, 3L);
  }

  @Test
  void send_202_resolvesNormally(WireMockRuntimeInfo info) {
    NotificationEvent event = lockEvent();
    stubFor(
        post(urlPathEqualTo("/notifications/send"))
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"data\":{\"notificationId\":\"n-1\",\"status\":\"QUEUED\","
                            + "\"acceptedAt\":\"2026-04-25T10:00:00Z\"},\"meta\":{}}")));

    client(info).send(event); // no throw

    // Idempotency key per ADR: wc-plan-{planId}-{to}-v{planVersion}
    verify(
        postRequestedFor(urlPathEqualTo("/notifications/send"))
            .withHeader(
                "X-Idempotency-Key",
                equalTo("wc-plan-" + event.planId() + "-LOCKED-v" + event.planVersion()))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(matchingJsonPath("$.eventType", equalTo("WEEKLY_PLAN_LOCKED")))
            .withRequestBody(
                matchingJsonPath("$.metadata.planId", equalTo(event.planId().toString()))));
  }

  @Test
  void send_409_treatedAsSuccess(WireMockRuntimeInfo info) {
    // ADR: "duplicate idempotency key -- *treated as success*". Don't throw, don't DLT.
    stubFor(
        post(urlPathEqualTo("/notifications/send"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":{\"code\":\"DUPLICATE\",\"priorNotificationId\":\"n-prior\"}}")));

    client(info).send(lockEvent()); // no throw
  }

  @Test
  void send_400_throwsValidationFailedException_noDltWorthy(WireMockRuntimeInfo info) {
    // ADR: 400 is non-recoverable; do not retry, do not DLT. Client surfaces it as a typed
    // exception so the wrapping sender can distinguish it from retryable WebClientResponseException
    // and skip both retry and DLT.
    stubFor(
        post(urlPathEqualTo("/notifications/send"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":{\"code\":\"VALIDATION_FAILED\",\"message\":\"bad templateVars\"}}")));

    assertThatThrownBy(() -> client(info).send(lockEvent()))
        .isInstanceOf(NotificationValidationException.class);
  }

  @Test
  void send_503_throwsForRetry(WireMockRuntimeInfo info) {
    stubFor(
        post(urlPathEqualTo("/notifications/send"))
            .willReturn(aResponse().withStatus(503).withBody("upstream down")));

    assertThatThrownBy(() -> client(info).send(lockEvent()))
        .isInstanceOf(WebClientResponseException.class);
  }

  @Test
  void send_serializesExpectedRequestPayload(WireMockRuntimeInfo info) {
    // The full request body shape per ADR-0002. Pinned so a future refactor of the client's
    // payload assembly still matches the upstream contract.
    NotificationEvent event = lockEvent();
    stubFor(post(urlPathEqualTo("/notifications/send")).willReturn(aResponse().withStatus(202)));

    client(info).send(event);

    String expectedBody =
        """
        {
          "eventType": "WEEKLY_PLAN_LOCKED",
          "channel": "EMAIL",
          "templateId": "weekly-commit.plan-locked.v1",
          "templateVars": {
            "planId": "%s",
            "fromState": "DRAFT",
            "toState": "LOCKED",
            "planVersion": 3
          },
          "metadata": {
            "sourceService": "weekly-commit-service",
            "planId": "%s"
          }
        }
        """
            .formatted(event.planId(), event.planId());

    verify(
        postRequestedFor(urlPathEqualTo("/notifications/send"))
            .withRequestBody(equalToJson(expectedBody, true, true)));
  }
}
