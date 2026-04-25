package com.acme.weeklycommit.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.integration.notification.NotificationClient;
import com.acme.weeklycommit.integration.notification.NotificationSender;
import com.acme.weeklycommit.integration.notification.ResilientNotificationSender;
import com.acme.weeklycommit.repo.NotificationDltRepository;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * End-to-end DLT replay: drive a real failure through {@link ResilientNotificationSender}, confirm
 * a row appears in {@code notification_dlt}, then call {@code POST
 * /api/v1/admin/notifications/dlt/{id}/replay} and assert the row is deleted while WireMock
 * observes a successful retry POST.
 *
 * <p>Closes the loop on the group-6 DLT replay endpoint: in group 6 we shipped {@code
 * DltReplayService} with a stubbed {@link NotificationSender}; group 7's {@link
 * ResilientNotificationSender} is the real producer of DLT rows. This IT proves both halves agree
 * on the payload shape and that the replay path actually re-issues the original event.
 *
 * <p>Activates a {@link TestConfiguration} that registers a {@link ResilientNotificationSender}
 * directly (bypassing the {@code @Profile("prod")} gate) so the IT exercises real semantics without
 * flipping the global profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminDltReplayIT.ResilientSenderTestConfig.class)
class AdminDltReplayIT {

  private static WireMockServer notificationSvc;

  @BeforeAll
  static void startWireMock() {
    notificationSvc = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    notificationSvc.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (notificationSvc != null) {
      notificationSvc.stop();
    }
  }

  @BeforeEach
  void resetState() {
    notificationSvc.resetAll();
    dltRepo.deleteAll();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresTestContainer.register(registry);
    registry.add("AUTH0_ISSUER_URI", () -> "https://test.invalid/");
    registry.add("AUTH0_AUDIENCE", () -> "test-audience");
    registry.add("weekly-commit.notification.base-url", () -> notificationSvc.baseUrl());
  }

  @Autowired private MockMvc mvc;
  @Autowired private NotificationSender sender;
  @Autowired private NotificationDltRepository dltRepo;

  private static JwtRequestPostProcessor adminJwt() {
    UUID employeeId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    UUID orgId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    return jwt()
        .jwt(b -> b.subject(employeeId.toString()).claim("org_id", orgId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  @Test
  void replayPath_writesDltOnFailure_thenReplayDeletesRowAndResends() throws Exception {
    // Step 1: notification-svc is "down" — every POST returns 503.
    notificationSvc.stubFor(
        post(urlPathEqualTo("/notifications/send"))
            .willReturn(aResponse().withStatus(503).withBody("simulated outage")));

    NotificationEvent event =
        new NotificationEvent(UUID.randomUUID(), PlanState.DRAFT, PlanState.LOCKED, 7L);

    // Drive the failure through the real sender. Test config caps retries at 2 to keep the
    // IT fast.
    sender.send(event);

    // Step 2: a DLT row is written.
    var dltRows = dltRepo.findAll();
    assertThat(dltRows).hasSize(1);
    UUID dltId = dltRows.get(0).getId();
    assertThat(dltRows.get(0).getEventType()).isEqualTo("PLAN_LOCKED");
    assertThat(dltRows.get(0).getPayload().get("planId").asText())
        .isEqualTo(event.planId().toString());

    // Two failed POSTs hit notification-svc (initial + 1 retry).
    notificationSvc.verify(2, postRequestedFor(urlPathEqualTo("/notifications/send")));

    // Step 3: notification-svc is "back up" — returns 202.
    notificationSvc.resetRequests();
    notificationSvc.resetMappings();
    notificationSvc.stubFor(
        post(urlPathEqualTo("/notifications/send")).willReturn(aResponse().withStatus(202)));

    // Step 4: ADMIN replays the DLT row.
    mvc.perform(post("/api/v1/admin/notifications/dlt/{id}/replay", dltId).with(adminJwt()))
        .andExpect(status().isAccepted());

    // Step 5: DLT row is deleted, replay POST hit notification-svc once.
    assertThat(dltRepo.findAll()).isEmpty();
    notificationSvc.verify(1, postRequestedFor(urlPathEqualTo("/notifications/send")));
  }

  /**
   * Wires {@link ResilientNotificationSender} as the active {@link NotificationSender}, bypassing
   * its {@code @Profile("prod")} gate. {@code @Primary} so it shadows {@code
   * LoggingNotificationSender} (which is {@code @Profile("!prod")} and would otherwise win in the
   * {@code test} profile).
   *
   * <p>Tight Retry config (2 attempts, 1ms wait) so the IT runs in seconds.
   */
  @TestConfiguration
  static class ResilientSenderTestConfig {

    @Bean
    @Primary
    NotificationSender resilientSenderForTest(
        NotificationClient client, NotificationDltRepository dltRepo, ObjectMapper objectMapper) {
      Retry retry =
          Retry.of(
              "test-notification",
              RetryConfig.custom()
                  .maxAttempts(2)
                  .waitDuration(Duration.ofMillis(1))
                  .retryExceptions(
                      org.springframework.web.reactive.function.client.WebClientResponseException
                          .class)
                  .build());
      CircuitBreaker breaker =
          CircuitBreaker.of(
              "test-notification",
              CircuitBreakerConfig.custom()
                  .slidingWindowSize(10)
                  .minimumNumberOfCalls(10)
                  .failureRateThreshold(50)
                  .waitDurationInOpenState(Duration.ofSeconds(30))
                  .build());
      return new ResilientNotificationSender(client, dltRepo, objectMapper, retry, breaker);
    }

    /**
     * Re-build the NotificationClient against the WireMock URL injected via @DynamicPropertySource.
     */
    @Bean
    @Primary
    NotificationClient testNotificationClient(
        @org.springframework.beans.factory.annotation.Value(
                "${weekly-commit.notification.base-url}")
            String baseUrl) {
      WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
      return new NotificationClient(webClient, Duration.ofSeconds(2));
    }
  }
}
