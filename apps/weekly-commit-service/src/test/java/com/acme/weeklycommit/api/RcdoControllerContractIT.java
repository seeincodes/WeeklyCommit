package com.acme.weeklycommit.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.integration.notification.NotificationSender;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test that locks both ends of the RCDO pass-through:
 *
 * <ul>
 *   <li>upstream RCDO JSON shape (the canonical body used by WireMock here mirrors the stub fixture
 *       in {@code docs/spikes/rcdo-sample-responses.json} -- the source of truth that the real
 *       service capture must match per ADR-0001),
 *   <li>UI-facing JSON shape (the picker contract in {@code libs/rtk-api-client/src/rcdo.ts}).
 * </ul>
 *
 * <p>Failure modes this test catches that the unit-level {@link RcdoControllerTest} cannot:
 *
 * <ul>
 *   <li>Jackson serialization drift between {@code SupportingOutcomeView} and {@code
 *       SupportingOutcomeResponse} (the unit test mocks the client and never round-trips real JSON
 *       through Jackson).
 *   <li>Wire-level mapping bugs in {@code RcdoClient} (URL paths, query params).
 *   <li>Resilience4j wiring on the new endpoints (a typo in {@code
 *       resilience4j.retry.instances.rcdo} would surface here too).
 * </ul>
 *
 * <p>Suffix is {@code IT} so it runs under Failsafe in {@code mvn verify}, alongside other Spring
 * boot integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RcdoControllerContractIT {

  private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID OUTCOME_ID = UUID.fromString("c4a1e7b2-9f28-4f3c-8a61-1e5f3c9d2b44");
  private static final UUID RC_ID = UUID.fromString("01010101-0101-0101-0101-010101010101");
  private static final UUID DO_ID = UUID.fromString("02020202-0202-0202-0202-020202020202");
  private static final UUID CO_ID = UUID.fromString("03030303-0303-0303-0303-030303030303");

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

  @Autowired private MockMvc mvc;

  // Same conditional-on-missing-bean dance as RcdoClientResilienceIT.
  @MockBean private NotificationSender notificationSender;

  private static JwtRequestPostProcessor validJwt() {
    return jwt()
        .jwt(builder -> builder.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

  /**
   * Canonical upstream JSON for a single Supporting Outcome -- mirrors the {@code "GET
   * /rcdo/supporting-outcomes/{id}?hydrate=full"} entry in {@code
   * docs/spikes/rcdo-sample-responses.json}. If that fixture file changes, this body must change
   * with it (and vice versa) -- documented in the ADR-0001 "Validation" checklist.
   */
  private static String upstreamSingleBody() {
    return """
        {
          "data": {
            "id": "%s",
            "label": "Alignment tooling GA",
            "active": true,
            "breadcrumb": {
              "rallyCry":          { "id": "%s", "label": "Unblock product-led growth" },
              "definingObjective": { "id": "%s", "label": "Product-led GTM" },
              "coreOutcome":       { "id": "%s", "label": "Tooling readiness" },
              "supportingOutcome": { "id": "%s", "label": "Alignment tooling GA" }
            }
          },
          "meta": {}
        }
        """
        .formatted(OUTCOME_ID, RC_ID, DO_ID, CO_ID, OUTCOME_ID);
  }

  private static String upstreamListBody() {
    return """
        {
          "data": [
            {
              "id": "%s",
              "label": "Alignment tooling GA",
              "active": true,
              "breadcrumb": {
                "rallyCry":          { "id": "%s", "label": "Unblock product-led growth" },
                "definingObjective": { "id": "%s", "label": "Product-led GTM" },
                "coreOutcome":       { "id": "%s", "label": "Tooling readiness" },
                "supportingOutcome": { "id": "%s", "label": "Alignment tooling GA" }
              }
            }
          ],
          "meta": { "totalCount": 1 }
        }
        """
        .formatted(OUTCOME_ID, RC_ID, DO_ID, CO_ID, OUTCOME_ID);
  }

  @Test
  void list_translatesUpstreamShapeToPickerShape() throws Exception {
    wireMock.stubFor(
        WireMock.get(urlPathEqualTo("/rcdo/supporting-outcomes"))
            .withQueryParam("orgId", equalTo(ORG_ID.toString()))
            .withQueryParam("active", equalTo("true"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(upstreamListBody())));

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(OUTCOME_ID.toString()))
        .andExpect(jsonPath("$.data[0].label").value("Alignment tooling GA"))
        .andExpect(jsonPath("$.data[0].active").value(true))
        .andExpect(
            jsonPath("$.data[0].breadcrumb.rallyCry.label").value("Unblock product-led growth"))
        .andExpect(jsonPath("$.data[0].breadcrumb.rallyCry.id").value(RC_ID.toString()))
        .andExpect(
            jsonPath("$.data[0].breadcrumb.definingObjective.label").value("Product-led GTM"))
        .andExpect(jsonPath("$.data[0].breadcrumb.coreOutcome.label").value("Tooling readiness"))
        .andExpect(
            jsonPath("$.data[0].breadcrumb.supportingOutcome.id").value(OUTCOME_ID.toString()));

    // Verify the upstream URL was hit with the JWT-derived orgId, not user input.
    wireMock.verify(WireMock.getRequestedFor(urlPathEqualTo("/rcdo/supporting-outcomes")));
  }

  @Test
  void getById_translatesUpstreamShapeToPickerShape() throws Exception {
    wireMock.stubFor(
        WireMock.get(urlPathEqualTo("/rcdo/supporting-outcomes/" + OUTCOME_ID))
            .withQueryParam("hydrate", equalTo("full"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(upstreamSingleBody())));

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes/{id}", OUTCOME_ID).with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(OUTCOME_ID.toString()))
        .andExpect(jsonPath("$.data.label").value("Alignment tooling GA"))
        .andExpect(jsonPath("$.data.breadcrumb.rallyCry.label").value("Unblock product-led growth"))
        .andExpect(jsonPath("$.data.breadcrumb.supportingOutcome.id").value(OUTCOME_ID.toString()));
  }

  @Test
  void getById_returns404WhenUpstreamReturns404() throws Exception {
    wireMock.stubFor(
        WireMock.get(urlPathMatching("/rcdo/supporting-outcomes/.*"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":{\"code\":\"SUPPORTING_OUTCOME_NOT_FOUND\",\"message\":\"gone\"}}")));

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes/{id}", UUID.randomUUID()).with(validJwt()))
        .andExpect(status().isNotFound());
  }
}
