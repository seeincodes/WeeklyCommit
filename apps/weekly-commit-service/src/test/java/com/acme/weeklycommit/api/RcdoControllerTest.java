package com.acme.weeklycommit.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.integration.rcdo.RcdoClient;
import com.acme.weeklycommit.integration.rcdo.SupportingOutcomeView;
import com.acme.weeklycommit.testsupport.WebMvcTestConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * @WebMvcTest slice for the RCDO pass-through controller. Covers both endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/v1/rcdo/supporting-outcomes} — list scoped to the JWT's org_id
 *   <li>{@code GET /api/v1/rcdo/supporting-outcomes/{id}} — single hydration; 404 propagates
 * </ul>
 *
 * <p>Backed by {@link RcdoClient} (mocked here). The picker shape is asserted directly against the
 * JSON tree because the picker contract — declared in {@code libs/rtk-api-client/src/rcdo.ts} — is
 * load-bearing for the UI.
 */
@WebMvcTest(controllers = RcdoController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class RcdoControllerTest {

  private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID OUTCOME_ID = UUID.fromString("c4a1e7b2-9f28-4f3c-8a61-1e5f3c9d2b44");
  private static final UUID RC_ID = UUID.fromString("01010101-0101-0101-0101-010101010101");
  private static final UUID DO_ID = UUID.fromString("02020202-0202-0202-0202-020202020202");
  private static final UUID CO_ID = UUID.fromString("03030303-0303-0303-0303-030303030303");

  @Autowired private MockMvc mvc;

  @MockBean private RcdoClient rcdoClient;

  /**
   * JWT with both required claims (sub + org_id). Single org per JWT — controller uses the JWT
   * org_id as the orgId arg to the upstream RCDO call so an IC can't query another org.
   */
  private static JwtRequestPostProcessor validJwt() {
    return jwt()
        .jwt(builder -> builder.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

  private static SupportingOutcomeView sampleOutcome() {
    return new SupportingOutcomeView(
        OUTCOME_ID,
        "Alignment tooling GA",
        true,
        new SupportingOutcomeView.Breadcrumb(
            new SupportingOutcomeView.Node(RC_ID, "Unblock product-led growth"),
            new SupportingOutcomeView.Node(DO_ID, "Product-led GTM"),
            new SupportingOutcomeView.Node(CO_ID, "Tooling readiness"),
            new SupportingOutcomeView.Node(OUTCOME_ID, "Alignment tooling GA")));
  }

  // -----------------------------------------------------------------------------------------------
  // GET /api/v1/rcdo/supporting-outcomes  (list — picker)
  // -----------------------------------------------------------------------------------------------

  @Test
  void list_returns200WithEnvelopedOutcomes() throws Exception {
    when(rcdoClient.findActiveSupportingOutcomes(eq(ORG_ID))).thenReturn(List.of(sampleOutcome()));

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(OUTCOME_ID.toString()))
        .andExpect(jsonPath("$.data[0].label").value("Alignment tooling GA"))
        .andExpect(jsonPath("$.data[0].active").value(true))
        // The 4-level breadcrumb shape is the picker contract -- assert each level has both id and
        // label per libs/rtk-api-client/src/rcdo.ts.
        .andExpect(
            jsonPath("$.data[0].breadcrumb.rallyCry.label").value("Unblock product-led growth"))
        .andExpect(jsonPath("$.data[0].breadcrumb.rallyCry.id").value(RC_ID.toString()))
        .andExpect(
            jsonPath("$.data[0].breadcrumb.definingObjective.label").value("Product-led GTM"))
        .andExpect(jsonPath("$.data[0].breadcrumb.coreOutcome.label").value("Tooling readiness"))
        .andExpect(
            jsonPath("$.data[0].breadcrumb.supportingOutcome.id").value(OUTCOME_ID.toString()))
        .andExpect(jsonPath("$.meta.now").isString());
  }

  @Test
  void list_passesJwtOrgIdToUpstream() throws Exception {
    // Other org id -- the JWT's ORG_ID is what should reach the client, not anything user-supplied.
    UUID otherOrg = UUID.randomUUID();
    when(rcdoClient.findActiveSupportingOutcomes(eq(otherOrg)))
        .thenReturn(List.of(sampleOutcome()));
    // Even if the client returns data for the wrong org, we should miss because we'll have asked
    // for ORG_ID -- which the mock isn't stubbed for, so it returns empty list by default.
    mvc.perform(get("/api/v1/rcdo/supporting-outcomes").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void list_returns401WhenNoJwt() throws Exception {
    mvc.perform(get("/api/v1/rcdo/supporting-outcomes")).andExpect(status().isUnauthorized());
  }

  @Test
  void list_returnsEmptyArrayWhenUpstreamReturnsEmpty() throws Exception {
    when(rcdoClient.findActiveSupportingOutcomes(eq(ORG_ID))).thenReturn(List.of());

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  // -----------------------------------------------------------------------------------------------
  // GET /api/v1/rcdo/supporting-outcomes/{id}  (single — hydration)
  // -----------------------------------------------------------------------------------------------

  @Test
  void getById_returns200WithEnvelopedOutcome() throws Exception {
    when(rcdoClient.findSupportingOutcome(eq(OUTCOME_ID))).thenReturn(Optional.of(sampleOutcome()));

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes/{id}", OUTCOME_ID).with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(OUTCOME_ID.toString()))
        .andExpect(jsonPath("$.data.label").value("Alignment tooling GA"))
        .andExpect(
            jsonPath("$.data.breadcrumb.rallyCry.label").value("Unblock product-led growth"));
  }

  @Test
  void getById_returns404WhenUpstreamSaysNotFound() throws Exception {
    // RcdoClient already maps upstream 404 -> Optional.empty() so the controller sees an empty
    // Optional.
    when(rcdoClient.findSupportingOutcome(eq(OUTCOME_ID))).thenReturn(Optional.empty());

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes/{id}", OUTCOME_ID).with(validJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getById_returns401WhenNoJwt() throws Exception {
    mvc.perform(get("/api/v1/rcdo/supporting-outcomes/{id}", OUTCOME_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getById_returns502WhenUpstreamFails() throws Exception {
    // 5xx propagates from RcdoClient as a WebClientResponseException after retry exhaustion.
    when(rcdoClient.findSupportingOutcome(eq(OUTCOME_ID)))
        .thenThrow(WebClientResponseException.create(503, "Service Unavailable", null, null, null));

    mvc.perform(get("/api/v1/rcdo/supporting-outcomes/{id}", OUTCOME_ID).with(validJwt()))
        .andExpect(status().isBadGateway());
  }
}
