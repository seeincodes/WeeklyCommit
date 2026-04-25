package com.acme.weeklycommit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.api.dto.RollupResponse;
import com.acme.weeklycommit.service.RollupService;
import com.acme.weeklycommit.testsupport.WebMvcTestConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RollupController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class RollupControllerTest {

  private static final UUID EMPLOYEE_ID =
      UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Autowired private MockMvc mvc;

  @MockBean private RollupService rollupService;

  private static JwtRequestPostProcessor validJwt() {
    return jwt().jwt(b -> b.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

  @Test
  void getTeamRollup_200WithEnvelope() throws Exception {
    UUID managerId = UUID.randomUUID();
    Map<String, Integer> tiers = Map.of("ROCK", 4, "PEBBLE", 6, "SAND", 2);
    RollupResponse rollup =
        new RollupResponse(
            new BigDecimal("0.9100"),
            new BigDecimal("0.7600"),
            tiers,
            3,
            2,
            List.of());
    when(rollupService.computeRollup(any(), any(), any())).thenReturn(rollup);

    mvc.perform(
            get("/api/v1/rollup/team")
                .param("managerId", managerId.toString())
                .param("weekStart", "2026-04-27")
                .with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.alignmentPct").value(0.91))
        .andExpect(jsonPath("$.data.completionPct").value(0.76))
        .andExpect(jsonPath("$.data.tierDistribution.ROCK").value(4))
        .andExpect(jsonPath("$.data.unreviewedCount").value(3))
        .andExpect(jsonPath("$.data.stuckCommitCount").value(2));
  }

  @Test
  void getTeamRollup_400WhenMissingManagerId() throws Exception {
    mvc.perform(get("/api/v1/rollup/team").param("weekStart", "2026-04-27").with(validJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getTeamRollup_unauthenticated_returns401() throws Exception {
    mvc.perform(
            get("/api/v1/rollup/team")
                .param("managerId", UUID.randomUUID().toString())
                .param("weekStart", "2026-04-27"))
        .andExpect(status().isUnauthorized());
  }
}
