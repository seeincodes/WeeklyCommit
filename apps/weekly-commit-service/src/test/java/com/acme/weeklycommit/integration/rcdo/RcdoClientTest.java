package com.acme.weeklycommit.integration.rcdo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Contract tests for {@link RcdoClient} against a WireMock-stubbed RCDO service. Pins the client's
 * behavior against the contract documented in docs/adr/0001-rcdo-contract.md.
 *
 * <p>Resilience4j retry / circuit breaker are <i>not</i> exercised here -- those are cross-cutting
 * AOP concerns wired by Spring at runtime. A separate {@code RcdoClientResilienceIT} (full-context
 * Spring boot) covers them. Keeping this test lightweight (no Spring) keeps the happy-path / 404 /
 * 5xx semantics easy to read.
 */
@WireMockTest
class RcdoClientTest {

  private RcdoClient client(WireMockRuntimeInfo info) {
    WebClient webClient =
        WebClient.builder()
            .baseUrl(info.getHttpBaseUrl())
            .defaultHeader("Authorization", "Bearer test-token")
            .build();
    return new RcdoClient(webClient, Duration.ofSeconds(2));
  }

  @Test
  void findSupportingOutcome_200_returnsHydratedView(WireMockRuntimeInfo info) {
    UUID id = UUID.fromString("c4a1e7b2-9f28-4f3c-8a61-1e5f3c9d2b44");
    stubFor(
        get(urlPathEqualTo("/rcdo/supporting-outcomes/" + id))
            .withQueryParam("hydrate", equalTo("full"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "data": {
                            "id": "%s",
                            "label": "Alignment tooling GA",
                            "active": true,
                            "breadcrumb": {
                              "rallyCry":          { "id": "00000000-0000-0000-0000-0000000000a1", "label": "Unblock product-led growth" },
                              "definingObjective": { "id": "00000000-0000-0000-0000-0000000000a2", "label": "Product-led GTM" },
                              "coreOutcome":       { "id": "00000000-0000-0000-0000-0000000000a3", "label": "Tooling readiness" },
                              "supportingOutcome": { "id": "%s", "label": "Alignment tooling GA" }
                            }
                          },
                          "meta": {}
                        }
                        """
                            .formatted(id, id))));

    Optional<SupportingOutcomeView> result = client(info).findSupportingOutcome(id);

    assertThat(result).isPresent();
    SupportingOutcomeView view = result.get();
    assertThat(view.id()).isEqualTo(id);
    assertThat(view.label()).isEqualTo("Alignment tooling GA");
    assertThat(view.active()).isTrue();
    assertThat(view.breadcrumb().rallyCry().label()).isEqualTo("Unblock product-led growth");
    assertThat(view.breadcrumb().definingObjective().label()).isEqualTo("Product-led GTM");
    assertThat(view.breadcrumb().coreOutcome().label()).isEqualTo("Tooling readiness");
    assertThat(view.breadcrumb().supportingOutcome().id()).isEqualTo(id);

    verify(
        getRequestedFor(urlPathEqualTo("/rcdo/supporting-outcomes/" + id))
            .withHeader("Authorization", equalTo("Bearer test-token")));
  }

  @Test
  void findSupportingOutcome_404_returnsEmpty(WireMockRuntimeInfo info) {
    // ADR-0001: 404 on a deleted/deactivated outcome is the *expected* behavior. Map it to
    // Optional.empty() so callers can render a "outcome removed" chip without try/catch.
    UUID id = UUID.randomUUID();
    stubFor(
        get(urlPathEqualTo("/rcdo/supporting-outcomes/" + id))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"unknown outcome\"}}")));

    assertThat(client(info).findSupportingOutcome(id)).isEmpty();
  }

  @Test
  void findSupportingOutcome_5xx_throwsForRetry(WireMockRuntimeInfo info) {
    // 503 is retryable per Resilience4j config -- but the client itself just propagates
    // WebClientResponseException. The retry happens at the AOP layer (separate IT).
    UUID id = UUID.randomUUID();
    stubFor(
        get(urlPathEqualTo("/rcdo/supporting-outcomes/" + id))
            .willReturn(aResponse().withStatus(503).withBody("upstream down")));

    assertThatThrownBy(() -> client(info).findSupportingOutcome(id))
        .isInstanceOf(WebClientResponseException.class);
  }

  @Test
  void findActiveSupportingOutcomes_200_returnsList(WireMockRuntimeInfo info) {
    UUID orgId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    UUID id1 = UUID.fromString("c4a1e7b2-9f28-4f3c-8a61-1e5f3c9d2b44");
    UUID id2 = UUID.fromString("d5b2f8c3-0e39-4f4d-9b72-2f8e4d0e3c55");
    stubFor(
        get(urlPathEqualTo("/rcdo/supporting-outcomes"))
            .withQueryParam("orgId", equalTo(orgId.toString()))
            .withQueryParam("active", equalTo("true"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "data": [
                            {
                              "id": "%s",
                              "label": "Alignment tooling GA",
                              "active": true,
                              "breadcrumb": {
                                "rallyCry":          { "id": "00000000-0000-0000-0000-0000000000a1", "label": "RC" },
                                "definingObjective": { "id": "00000000-0000-0000-0000-0000000000a2", "label": "DO" },
                                "coreOutcome":       { "id": "00000000-0000-0000-0000-0000000000a3", "label": "CO" },
                                "supportingOutcome": { "id": "%s", "label": "Alignment tooling GA" }
                              }
                            },
                            {
                              "id": "%s",
                              "label": "Pipeline observability",
                              "active": true,
                              "breadcrumb": {
                                "rallyCry":          { "id": "00000000-0000-0000-0000-0000000000a1", "label": "RC" },
                                "definingObjective": { "id": "00000000-0000-0000-0000-0000000000a2", "label": "DO" },
                                "coreOutcome":       { "id": "00000000-0000-0000-0000-0000000000a4", "label": "CO2" },
                                "supportingOutcome": { "id": "%s", "label": "Pipeline observability" }
                              }
                            }
                          ],
                          "meta": { "totalCount": 2 }
                        }
                        """
                            .formatted(id1, id1, id2, id2))));

    List<SupportingOutcomeView> result = client(info).findActiveSupportingOutcomes(orgId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(id1);
    assertThat(result.get(0).label()).isEqualTo("Alignment tooling GA");
    assertThat(result.get(1).id()).isEqualTo(id2);
  }

  @Test
  void findActiveSupportingOutcomes_emptyList_returnsEmptyList(WireMockRuntimeInfo info) {
    UUID orgId = UUID.randomUUID();
    stubFor(
        get(urlPathEqualTo("/rcdo/supporting-outcomes"))
            .withQueryParam("orgId", equalTo(orgId.toString()))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"data\":[],\"meta\":{\"totalCount\":0}}")));

    assertThat(client(info).findActiveSupportingOutcomes(orgId)).isEmpty();
  }
}
