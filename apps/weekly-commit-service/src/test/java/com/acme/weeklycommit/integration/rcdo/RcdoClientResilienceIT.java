package com.acme.weeklycommit.integration.rcdo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.weeklycommit.integration.notification.NotificationSender;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Verifies that the {@code @Retry @CircuitBreaker} annotations on {@link RcdoClient} are wired
 * through the Spring proxy and observe the {@code rcdo} Resilience4j instance from {@code
 * application.yml}. The plain unit tests construct {@code RcdoClient} directly so they bypass AOP
 * -- this IT is the only place where a typo in {@code resilience4j.retry.instances.rcdo} would be
 * caught.
 *
 * <p>Manual {@link WireMockServer} lifecycle (rather than {@code @WireMockTest}) so the URL is
 * known before {@link DynamicPropertySource} fires.
 */
@SpringBootTest
@ActiveProfiles("test")
class RcdoClientResilienceIT {

  private static WireMockServer wireMock;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @BeforeEach
  void resetStubs() {
    wireMock.resetAll();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresTestContainer.register(registry);
    registry.add("AUTH0_ISSUER_URI", () -> "https://test.invalid/");
    registry.add("AUTH0_AUDIENCE", () -> "test-audience");
    registry.add("weekly-commit.rcdo.base-url", () -> wireMock.baseUrl());
  }

  @Autowired private RcdoClient client;

  // NotificationSender bean has the self-conflicting @ConditionalOnMissingBean issue when the
  // prod profile is inactive (see OpenApiSpecGenerationIT). Mock it so context load is
  // deterministic.
  @MockBean private NotificationSender notificationSender;

  @Test
  void findSupportingOutcome_5xx_triggersRetryBudget() {
    // 503 on every call -> retry fires. application.yml configures rcdo.maxAttempts=3.
    wireMock.stubFor(
        get(urlPathMatching("/rcdo/supporting-outcomes/.*"))
            .willReturn(aResponse().withStatus(503).withBody("upstream down")));

    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> client.findSupportingOutcome(id))
        .isInstanceOf(WebClientResponseException.class);

    // 3 attempts == 3 wire calls if @Retry is wired correctly.
    wireMock.verify(3, getRequestedFor(urlPathMatching("/rcdo/supporting-outcomes/.*")));
  }
}
